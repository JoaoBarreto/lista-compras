package com.example.listacompras.data.sync

import com.example.listacompras.data.remote.AuthInterceptor
import com.example.listacompras.data.remote.PocketBaseApiFactory
import com.example.listacompras.data.remote.TokenProvider
import com.example.listacompras.data.repository.CountingSyncScheduler
import com.example.listacompras.data.repository.FakeActiveList
import com.example.listacompras.data.repository.FakeItemDao
import com.example.listacompras.data.repository.FakeEventDao
import com.example.listacompras.data.repository.FakeEventTypeDao
import com.example.listacompras.data.repository.FakePendingOpDao
import com.example.listacompras.data.repository.TransactionRunner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RealtimeClientTest {

    private class Post(val auth: String?, val body: String)

    private val server = MockWebServer()
    private val gets = AtomicInteger()
    private val posts = CopyOnWriteArrayList<Post>()
    private val pulls = AtomicInteger()
    private var pullResult = SyncOutcome.Done
    private val outboxRuns = AtomicInteger()
    private var outboxResult = SyncOutcome.Done
    private var pullGate: CompletableDeferred<Unit>? = null
    private var subscribeCode = 204
    private var subscribeCodeFor: ((String) -> Int)? = null
    private var eventForFirstStream: String? = null
    private var notEventStream = false
    private val backoffAttempts = CopyOnWriteArrayList<Int>()
    private val tokens = MutableStateFlow<String?>("tok1")
    private val network = MutableStateFlow(true)
    private val reachability = ServerReachability()
    private val itemDao = FakeItemDao()
    private val scheduler = CountingSyncScheduler()
    private val logs = CopyOnWriteArrayList<String>()

    private fun sse(clientId: String, extra: String? = null) = MockResponse.Builder()
        .addHeader("Content-Type", "text/event-stream")
        .body(
            "id: $clientId\nevent: PB_CONNECT\ndata: {\"clientId\":\"$clientId\"}\n\n" +
                (extra?.let { "event: items/*\ndata: $it\n\n" } ?: ""),
        )
        .build()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.method == "GET") {
                    val n = gets.incrementAndGet()
                    if (notEventStream) {
                        MockResponse.Builder().addHeader("Content-Type", "application/json").body("{}").build()
                    } else {
                        sse("c$n", if (n == 1) eventForFirstStream else null)
                    }
                } else {
                    posts += Post(request.headers["Authorization"], request.body?.utf8().orEmpty())
                    MockResponse.Builder().code(subscribeCodeFor?.invoke(posts.last().body) ?: subscribeCode).build()
                }
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun newClient(): RealtimeClient {
        val json = PocketBaseApiFactory.json
        val http = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(TokenProvider { tokens.value }))
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val tx = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }
        val applier = RealtimeEventApplier(
            itemDao, FakeEventTypeDao(), FakeEventDao(), FakePendingOpDao(), FakeActiveList("list1"), tx, SyncLock(), json,
        ) { logs += it }
        return RealtimeClient(
            sseClient = http,
            baseUrl = server.url("/").toString(),
            api = PocketBaseApiFactory.create(server.url("/").toString(), http),
            tokens = tokens,
            network = network,
            reachability = reachability,
            applier = applier,
            outbox = OutboxSender { outboxRuns.incrementAndGet(); outboxResult },
            pull = FullPull { pulls.incrementAndGet(); pullGate?.await(); pullResult },
            scheduler = scheduler,
            json = json,
            backoff = Backoff { attempt -> backoffAttempts += attempt; 5 },
            log = { logs += it },
        )
    }

    private fun runClient(block: suspend () -> Unit) = runBlocking {
        val job = launch(Dispatchers.IO) { newClient().run() }
        try {
            withTimeout(10_000) { block() }
        } finally {
            job.cancelAndJoin()
        }
    }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        while (!condition()) delay(10)
    }

    @Test
    fun `subscribes with the client id and token, pulls, then applies events`() {
        eventForFirstStream = """{"action":"create","record":{"id":"a","list":"list1","name":"Leite","checked":false,""" +
            """"added_by":"u","created":"2026-10-29 12:00:00.000Z","updated":"2026-10-29 12:00:00.000Z"}}"""

        runClient { awaitUntil { "a" in itemDao.items.value } }

        val post = posts.first()
        assertEquals("tok1", post.auth)
        assertEquals("""{"clientId":"c1","subscriptions":["items/*","event_types/*","events/*"]}""", post.body)
        assertTrue(pulls.get() >= 1)
        assertTrue(scheduler.requests >= 1)
        assertEquals("Leite", itemDao.items.value.getValue("a").name)
    }

    @Test
    fun `after the stream closes it reconnects with a new client id and pulls again`() {
        runClient { awaitUntil { posts.size >= 2 && pulls.get() >= 2 } }

        assertEquals(listOf("c1", "c2"), posts.take(2).map { Regex("\"clientId\":\"(\\w+)\"").find(it.body)!!.groupValues[1] })
        assertTrue(pulls.get() >= 2)
    }

    @Test
    fun `a new token restarts the connection and subscribes with it`() {
        runClient {
            awaitUntil { posts.isNotEmpty() }
            tokens.value = "tok2"
            awaitUntil { posts.any { it.auth == "tok2" } }
        }
    }

    @Test
    fun `a failed subscribe is retried`() {
        subscribeCode = 404

        runClient { awaitUntil { gets.get() >= 3 } }

        assertEquals(0, pulls.get())
    }

    @Test
    fun `if the calendar topics are rejected it falls back to items only and still pulls`() {
        // Only a subscription that includes the calendar topics is refused.
        subscribeCodeFor = { body -> if ("events/*" in body) 400 else 204 }

        runClient { awaitUntil { pulls.get() >= 1 } }

        assertEquals("""{"clientId":"c1","subscriptions":["items/*"]}""", posts[1].body)
    }

    @Test
    fun `a failed pull is treated as a lost connection`() {
        pullResult = SyncOutcome.Retry

        runClient { awaitUntil { pulls.get() >= 2 } }

        assertEquals(0, scheduler.requests)
    }

    @Test
    fun `queued changes are sent before the pull on every connection`() {
        runClient { awaitUntil { pulls.get() >= 1 } }

        assertTrue(outboxRuns.get() >= 1)
    }

    @Test
    fun `a failed outbox run skips the pull and reconnects`() {
        outboxResult = SyncOutcome.Retry

        runClient { awaitUntil { outboxRuns.get() >= 2 } }

        assertEquals(0, pulls.get())
        assertEquals(0, scheduler.requests)
    }

    @Test
    fun `losing the session stops reconnecting`() {
        pullResult = SyncOutcome.AuthLost

        runClient {
            awaitUntil { pulls.get() >= 1 }
            delay(300)
        }

        assertEquals(1, gets.get())
    }

    @Test
    fun `logging out cancels the connection and waits`() {
        runClient {
            awaitUntil { gets.get() >= 1 }
            tokens.value = null
            delay(200)
            val after = gets.get()
            delay(300)
            assertEquals(after, gets.get())
        }
    }

    @Test
    fun `a response that is not an event stream keeps retrying instead of crashing`() {
        notEventStream = true

        runClient { awaitUntil { gets.get() >= 3 } }

        assertEquals(0, posts.size)
    }

    @Test
    fun `backoff grows while the pull keeps failing and is not reset by a successful subscribe`() {
        pullResult = SyncOutcome.Retry

        runClient { awaitUntil { backoffAttempts.size >= 4 } }

        assertEquals(listOf(0, 1, 2, 3), backoffAttempts.take(4))
    }

    @Test
    fun `backoff restarts after a full successful connection`() {
        runClient { awaitUntil { backoffAttempts.size >= 3 } }

        assertTrue(backoffAttempts.take(3).all { it == 0 })
    }

    @Test
    fun `reachability becomes Reachable only after subscribing and pulling`() {
        runClient { awaitUntil { reachability.state.value == Reachability.Reachable } }

        assertTrue(pulls.get() >= 1)
    }

    @Test
    fun `reachability is Unreachable while the pull keeps failing`() {
        pullResult = SyncOutcome.Retry

        runClient { awaitUntil { reachability.state.value == Reachability.Unreachable } }
    }

    @Test
    fun `going offline stops the connection and coming back starts a fresh one`() {
        runClient {
            awaitUntil { reachability.state.value == Reachability.Reachable }
            network.value = false
            delay(200)
            val frozen = gets.get()
            delay(300)
            assertEquals(frozen, gets.get())

            network.value = true
            awaitUntil { gets.get() > frozen }
            awaitUntil { reachability.state.value == Reachability.Reachable }
        }
    }

    @Test
    fun `coming back online restarts the backoff from zero`() {
        pullResult = SyncOutcome.Retry
        runClient {
            awaitUntil { backoffAttempts.size >= 3 }
            network.value = false
            delay(100)
            network.value = true
            val before = backoffAttempts.size
            awaitUntil { backoffAttempts.size > before }
            assertEquals(0, backoffAttempts[before])
        }
    }

    @Test
    fun `reachability resets to Unknown as soon as the network drops`() {
        runClient {
            awaitUntil { reachability.state.value == Reachability.Reachable }

            network.value = false

            awaitUntil { reachability.state.value == Reachability.Unknown }
        }
    }

    @Test
    fun `after the network returns the state is not Reachable until the pull completes`() {
        runClient {
            awaitUntil { reachability.state.value == Reachability.Reachable }
            pullGate = CompletableDeferred()

            network.value = false
            awaitUntil { reachability.state.value == Reachability.Unknown }
            network.value = true
            // A new connection subscribes and then blocks in the pull; nothing may claim Reachable.
            val pullsBefore = pulls.get()
            awaitUntil { pulls.get() > pullsBefore }
            delay(150)
            assertEquals(Reachability.Unknown, reachability.state.value)

            pullGate!!.complete(Unit)
            awaitUntil { reachability.state.value == Reachability.Reachable }
        }
    }

    @Test
    fun `the timeline is logged for diagnosis`() {
        runClient {
            awaitUntil { reachability.state.value == Reachability.Reachable }
            network.value = false
            awaitUntil { logs.any { it == "Realtime loop stopped" } }
            network.value = true
            awaitUntil { logs.count { it == "Realtime loop started" } >= 2 }
        }

        assertTrue(logs.contains("Realtime network=false"))
        assertTrue(logs.contains("Realtime network=true"))
    }
}
