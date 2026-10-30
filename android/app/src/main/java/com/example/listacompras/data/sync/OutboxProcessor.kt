package com.example.listacompras.data.sync

import com.example.listacompras.data.local.dao.ItemDao
import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.local.entity.OpType
import com.example.listacompras.data.local.entity.PendingOpEntity
import com.example.listacompras.data.local.entity.SyncMetaEntity
import com.example.listacompras.data.remote.PocketBaseApi
import com.example.listacompras.data.remote.TokenProvider
import com.example.listacompras.data.remote.dto.ItemDto
import com.example.listacompras.data.repository.TransactionRunner
import com.example.listacompras.domain.model.SyncState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import java.io.IOException

enum class SyncOutcome {
    /** The outbox is empty. */
    Done,

    /** Network or server trouble: operations are kept and the run should be retried with backoff. */
    Retry,

    /** The session ended (refresh rejected); retrying is pointless until the next login. */
    AuthLost,
}

/**
 * Sends the outbox to the server in order, one operation at a time.
 *
 * Contract with [OutboxCoalescer]: `attempts` is committed *before* each request, so a worker killed
 * mid-request still leaves `attempts > 0` on a `CREATE` that may have reached the server. Runs are
 * serialized by the shared [SyncLock], so nothing is in flight while the outbox is coalesced.
 *
 * The server's response is not written back to Room: that is the reconciler's job.
 */
class OutboxProcessor(
    private val pendingOpDao: PendingOpDao,
    private val itemDao: ItemDao,
    private val syncMetaDao: SyncMetaDao,
    private val transactions: TransactionRunner,
    private val coalescer: OutboxCoalescer,
    private val api: PocketBaseApi,
    private val tokenProvider: TokenProvider,
    private val json: Json,
    private val log: (String) -> Unit,
    private val lock: SyncLock,
) {

    private enum class Step { Sent, Discarded, Retry, AuthLost }

    suspend fun process(): SyncOutcome = lock.mutex.withLock { runPasses() }

    private suspend fun runPasses(): SyncOutcome {
        // New operations may be written while a pass runs, so repeat until the outbox is empty.
        // Every pass either removes each operation it visits or returns, so this terminates.
        while (true) {
            coalescer.coalesceOutbox()
            val ops = pendingOpDao.getAll()
            if (ops.isEmpty()) return SyncOutcome.Done
            for (op in ops) {
                when (send(op)) {
                    Step.Sent, Step.Discarded -> Unit
                    Step.Retry -> return SyncOutcome.Retry
                    Step.AuthLost -> return SyncOutcome.AuthLost
                }
            }
        }
    }

    private suspend fun send(op: PendingOpEntity): Step {
        val wasAttempted = op.attempts > 0
        pendingOpDao.incrementAttempts(op.opId)
        return try {
            execute(op, wasAttempted)
            finish(op, discarded = false)
            Step.Sent
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            handleHttpError(op, e.code())
        } catch (e: SerializationException) {
            // A payload that cannot be parsed will never succeed.
            discard(op, "malformed payload")
        } catch (e: IllegalArgumentException) {
            discard(op, "malformed payload")
        } catch (e: IOException) {
            Step.Retry
        }
    }

    private suspend fun execute(op: PendingOpEntity, wasAttempted: Boolean) {
        when (op.type) {
            OpType.CREATE -> {
                val dto = json.decodeFromString<ItemDto>(op.payloadJson)
                try {
                    api.createItem(dto)
                } catch (e: HttpException) {
                    // A retried CREATE whose first response was lost is rejected as a duplicate id.
                    if (!(e.code() == 400 && wasAttempted && existsOnServer(dto.id))) throw e
                }
            }
            OpType.UPDATE -> api.updateItem(op.itemId, json.parseToJsonElement(op.payloadJson).jsonObject)
            OpType.DELETE -> {
                val response = api.deleteItem(op.itemId)
                // Retrofit does not throw for Response<Unit>; 404 means it is already gone.
                if (!response.isSuccessful && response.code() != 404) throw HttpException(response)
            }
        }
    }

    private suspend fun existsOnServer(id: String): Boolean = try {
        api.getItem(id)
        true
    } catch (e: HttpException) {
        if (e.code() == 404) false else throw e
    }

    private suspend fun handleHttpError(op: PendingOpEntity, code: Int): Step =
        when (classifyHttp(code, tokenProvider.token() != null)) {
            HttpVerdict.Retry -> Step.Retry
            HttpVerdict.AuthLost -> Step.AuthLost
            // 400, 403, 404 (on CREATE/UPDATE): it will never succeed, so do not block the queue.
            HttpVerdict.Discard -> discard(op, "HTTP $code")
        }

    private suspend fun discard(op: PendingOpEntity, reason: String): Step {
        log("Discarded ${op.type} operation: $reason")
        finish(op, discarded = true)
        return Step.Discarded
    }

    /** Removes the op and, unless newer ops exist for the item, marks it synced, all atomically. */
    private suspend fun finish(op: PendingOpEntity, discarded: Boolean) {
        transactions.run {
            pendingOpDao.delete(op.opId)
            if (pendingOpDao.getForItem(op.itemId).isEmpty()) {
                itemDao.getById(op.itemId)?.takeIf { it.syncState != SyncState.SYNCED }
                    ?.let { itemDao.upsert(it.copy(syncState = SyncState.SYNCED)) }
            }
            if (discarded) {
                val count = syncMetaDao.get(DISCARDED_KEY)?.toLongOrNull() ?: 0L
                syncMetaDao.put(SyncMetaEntity(DISCARDED_KEY, (count + 1).toString()))
            }
        }
    }

    companion object {
        /** Number of operations dropped as unsendable; the sync status (4.4) shows an error from it. */
        const val DISCARDED_KEY = "discardedOps"
    }
}
