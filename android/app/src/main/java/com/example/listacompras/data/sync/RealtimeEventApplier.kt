package com.example.listacompras.data.sync

import com.example.listacompras.data.local.dao.ItemDao
import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.remote.dto.RealtimeRecordEvent
import com.example.listacompras.data.repository.ActiveListProvider
import com.example.listacompras.data.repository.TransactionRunner
import com.example.listacompras.data.repository.toEntity
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Applies one realtime record event to Room, with the same rules as the [Reconciler]: an item with
 * pending local operations is left alone (its local version is newer; this also covers the echo of
 * this phone's own change, which usually arrives before the outbox entry is removed). Pending ids
 * are read inside the transaction. Runs under the shared [SyncLock], so an event that arrives
 * during a pull is applied after the snapshot and never overwritten by it.
 *
 * Bad input (malformed JSON, unknown action, another list) is ignored and never drops the connection.
 */
class RealtimeEventApplier(
    private val itemDao: ItemDao,
    private val pendingOpDao: PendingOpDao,
    private val activeList: ActiveListProvider,
    private val transactions: TransactionRunner,
    private val lock: SyncLock,
    private val json: Json,
    private val log: (String) -> Unit,
) {

    suspend fun apply(data: String) {
        val event = try {
            json.decodeFromString<RealtimeRecordEvent>(data)
        } catch (e: Exception) {
            // Decoding does not suspend, so this cannot swallow a cancellation. Covers bad JSON and
            // field values the serializers reject (e.g. an unparseable date). Never drop the connection.
            log("Ignored malformed realtime event")
            return
        }
        if (event.action !in ACTIONS) {
            log("Ignored realtime action ${event.action}")
            return
        }
        lock.mutex.withLock {
            if (event.record.list !in activeList.knownListIds()) return@withLock
            transactions.run {
                if (event.record.id in pendingOpDao.getPendingItemIds()) return@run
                if (event.action == DELETE) itemDao.deleteById(event.record.id)
                else itemDao.upsert(event.record.toEntity())
            }
            log("Applied realtime ${event.action}")
        }
    }

    private companion object {
        const val DELETE = "delete"
        val ACTIONS = setOf("create", "update", DELETE)
    }
}
