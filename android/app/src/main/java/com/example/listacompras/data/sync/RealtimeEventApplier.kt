package com.example.listacompras.data.sync

import com.example.listacompras.data.local.dao.EventDao
import com.example.listacompras.data.local.dao.EventTypeDao
import com.example.listacompras.data.local.dao.ItemDao
import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.remote.dto.EventDto
import com.example.listacompras.data.remote.dto.EventTypeDto
import com.example.listacompras.data.remote.dto.RealtimeRawEvent
import com.example.listacompras.data.remote.dto.RealtimeRecordEvent
import com.example.listacompras.data.repository.ActiveListProvider
import com.example.listacompras.data.repository.TransactionRunner
import com.example.listacompras.data.repository.toEntity
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

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
    private val eventTypeDao: EventTypeDao,
    private val eventDao: EventDao,
    private val pendingOpDao: PendingOpDao,
    private val activeList: ActiveListProvider,
    private val transactions: TransactionRunner,
    private val lock: SyncLock,
    private val json: Json,
    private val log: (String) -> Unit,
) {

    /**
     * [topic] is the SSE event name, which PocketBase sets to the subscription (the collection name
     * followed by a slash and a star). Anything else is treated as an items event.
     */
    suspend fun apply(data: String, topic: String? = null) {
        try {
            when (topic) {
                SyncCollections.topic(SyncCollections.EVENTS) -> applyCalendar(SyncCollections.EVENTS, data)
                SyncCollections.topic(SyncCollections.EVENT_TYPES) -> applyCalendar(SyncCollections.EVENT_TYPES, data)
                else -> applyItem(data)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Bad JSON or field values the serializers reject (e.g. an unparseable date). Never drop the connection.
            log("Ignored malformed realtime event")
        }
    }

    private suspend fun applyItem(data: String) {
        val event = json.decodeFromString<RealtimeRecordEvent>(data)
        if (event.action !in ACTIONS) {
            log("Ignored realtime action ${event.action}")
            return
        }
        lock.mutex.withLock {
            if (event.record.list !in activeList.knownListIds()) return@withLock
            transactions.run {
                if (event.record.id in pendingOpDao.getPendingItemIds(SyncCollections.ITEMS)) return@run
                if (event.action == DELETE) itemDao.deleteById(event.record.id)
                else itemDao.upsert(event.record.toEntity())
            }
            log("Applied realtime ${event.action}")
        }
    }

    private suspend fun applyCalendar(collection: String, data: String) {
        val event = json.decodeFromString<RealtimeRawEvent>(data)
        if (event.action !in ACTIONS) {
            log("Ignored realtime action ${event.action}")
            return
        }
        val id = event.record["id"]?.jsonPrimitive?.content ?: return
        // Decode before taking the lock: a record that cannot be decoded is ignored as a whole.
        val upsert: (suspend () -> Unit)? = if (event.action == DELETE) null else when (collection) {
            SyncCollections.EVENTS -> json.decodeFromJsonElement<EventDto>(event.record).toEntity().let { e -> suspend { eventDao.upsert(e) } }
            else -> json.decodeFromJsonElement<EventTypeDto>(event.record).toEntity().let { e -> suspend { eventTypeDao.upsert(e) } }
        }
        lock.mutex.withLock {
            transactions.run {
                if (id in pendingOpDao.getPendingItemIds(collection)) return@run
                when {
                    upsert != null -> upsert()
                    collection == SyncCollections.EVENTS -> eventDao.deleteById(id)
                    else -> eventTypeDao.deleteById(id)
                }
            }
            log("Applied realtime ${event.action} ($collection)")
        }
    }

    private companion object {
        const val DELETE = "delete"
        val ACTIONS = setOf("create", "update", DELETE)
    }
}
