package com.example.listacompras.data.sync

import com.example.listacompras.data.remote.PocketBaseApi
import com.example.listacompras.data.remote.dto.ConnectEvent
import com.example.listacompras.data.remote.dto.SubscribeRequest
import com.example.listacompras.domain.repository.RealtimeSync
import com.example.listacompras.domain.repository.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import retrofit2.HttpException
import java.io.IOException
import kotlin.random.Random

/** Delay before reconnect attempt number [attempt] (0-based). */
fun interface Backoff {
    fun delayMillis(attempt: Int): Long

    companion object {
        /** 1 s doubling up to 30 s, with ±20 % jitter so the two phones do not retry in lockstep. */
        val Default = Backoff { attempt ->
            val base = minOf(30_000L, 1_000L shl minOf(attempt, 5))
            (base * (0.8 + Random.nextDouble() * 0.4)).toLong()
        }
    }
}

/**
 * Live updates over Server-Sent Events (PocketBase realtime).
 *
 * Per connection: connect, wait for `PB_CONNECT` (it carries the `clientId`), subscribe, full pull,
 * then apply events. Subscribing before the pull means nothing between the snapshot and the
 * subscription is lost. Every reconnect gets a new `clientId`, so it subscribes and pulls again.
 * The server closes idle streams after 5 minutes (and any stream after 30), which is just another
 * reconnect.
 *
 * The subscription is authorized with the token used for the subscribe request, so when the token
 * changes (refresh or re-login) the connection is restarted; logging out cancels it.
 */
class RealtimeClient(
    private val sseClient: OkHttpClient,
    private val baseUrl: String,
    private val api: PocketBaseApi,
    private val tokens: Flow<String?>,
    private val network: Flow<Boolean>,
    private val reachability: ServerReachability,
    private val applier: RealtimeEventApplier,
    private val pull: FullPull,
    private val scheduler: SyncScheduler,
    private val json: Json,
    private val backoff: Backoff = Backoff.Default,
    private val log: (String) -> Unit,
) : RealtimeSync {

    private class SseMessage(val type: String?, val id: String?, val data: String)

    /** The connection ended in a way that should be retried after a delay. */
    private class ConnectionLost(message: String, cause: Throwable? = null) : Exception(message, cause)

    private class SessionEnded : Exception()

    override suspend fun run() = coroutineScope {
        // Reachability must not wait for the connect loop to restart: if that restart is delayed,
        // the status would keep showing the stale "Reachable" (i.e. "Synced") while disconnected.
        launch {
            network.collect { online ->
                log("Realtime network=$online")
                reachability.onNetworkChanged(online)
            }
        }
        // Connect only with a token *and* a network. Every offline -> online change (and every new
        // token) starts a fresh loop with no accumulated backoff. This also kills a "zombie" stream:
        // an idle connection through the VPN can survive airplane mode without ever erroring.
        combine(tokens, network) { token, online -> token.takeIf { online } }
            .distinctUntilChanged()
            .collectLatest { token ->
                if (token != null) {
                    log("Realtime loop started")
                    reachability.markUnknown()
                    connectLoop()
                }
            }
    }

    private suspend fun connectLoop() {
        try {
            reconnectForever()
        } finally {
            log("Realtime loop stopped")
        }
    }

    private suspend fun reconnectForever() {
        var attempt = 0
        while (true) {
            try {
                connectOnce(onConnected = { attempt = 0 })
                log("Realtime stream closed")
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionEnded) {
                return
            } catch (e: ConnectionLost) {
                reachability.markUnreachable()
                log("Realtime connection lost: ${e.message}")
            } catch (e: Exception) {
                reachability.markUnreachable()
                // Anything else (network error, a proxy answering with HTML, a malformed date...)
                // must not escape into the activity's scope and crash the app. Log only the type.
                log("Realtime connection lost: ${e.javaClass.simpleName}")
            }
            delay(backoff.delayMillis(attempt++))
        }
    }

    private suspend fun connectOnce(onConnected: () -> Unit) {
        log("Realtime connecting")
        messages().collect { message ->
            if (message.type == CONNECT_EVENT) {
                handshake(clientIdOf(message), onConnected)
            } else {
                // Events are only for our one subscription; anything unparseable is ignored there.
                log("Realtime event received: ${message.type}")
                applier.apply(message.data)
            }
        }
    }

    private suspend fun handshake(clientId: String, onConnected: () -> Unit) {
        val response = try {
            api.subscribe(SubscribeRequest(clientId, listOf(TOPIC)))
        } catch (e: HttpException) {
            throw ConnectionLost("subscribe HTTP ${e.code()}", e)
        }
        if (!response.isSuccessful) throw ConnectionLost("subscribe HTTP ${response.code()}")
        log("Realtime subscribed")
        when (pull.pull()) {
            SyncOutcome.Done -> Unit
            SyncOutcome.Retry -> throw ConnectionLost("pull failed")
            SyncOutcome.AuthLost -> throw SessionEnded()
        }
        log("Realtime pull done")
        // Only a connection that subscribed *and* pulled counts as healthy. Resetting earlier would
        // make a persistently failing pull reconnect every second, forever.
        onConnected()
        reachability.markReachable()
        scheduler.requestSync()
    }

    private fun clientIdOf(message: SseMessage): String =
        try {
            json.decodeFromString<ConnectEvent>(message.data).clientId
        } catch (e: SerializationException) {
            // The JS SDK reads the id from the SSE `id:` field of this event, so fall back to it.
            message.id ?: throw ConnectionLost("PB_CONNECT without clientId", e)
        }

    /**
     * Messages of one SSE connection. The listener only buffers (it runs on OkHttp's thread and must
     * never block); the flow completes when the server closes the stream and fails on errors.
     */
    private fun messages(): Flow<SseMessage> = callbackFlow<SseMessage> {
        val request = Request.Builder()
            .url(baseUrl + "api/realtime")
            .header("Accept", "text/event-stream")
            .build()
        val source = EventSources.createFactory(sseClient).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    trySend(SseMessage(type, id, data))
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    close(t ?: IOException("SSE failed with HTTP ${response?.code}"))
                }
            },
        )
        awaitClose { source.cancel() }
    }.buffer(Channel.UNLIMITED)

    companion object {
        /** Every record of the `items` collection. The bare `items` form is deprecated server-side. */
        const val TOPIC = "items/*"
        private const val CONNECT_EVENT = "PB_CONNECT"
    }
}
