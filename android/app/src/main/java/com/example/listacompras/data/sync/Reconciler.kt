package com.example.listacompras.data.sync

import com.example.listacompras.data.local.dao.EventDao
import com.example.listacompras.data.local.dao.EventTypeDao
import com.example.listacompras.data.local.dao.ItemDao
import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.local.entity.SyncMetaEntity
import com.example.listacompras.data.remote.PocketBaseApi
import com.example.listacompras.data.remote.TokenProvider
import com.example.listacompras.data.remote.dto.EventDto
import com.example.listacompras.data.remote.dto.EventTypeDto
import com.example.listacompras.data.remote.dto.ItemDto
import com.example.listacompras.data.repository.ActiveListProvider
import com.example.listacompras.data.repository.TransactionRunner
import com.example.listacompras.data.repository.toEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import com.example.listacompras.domain.model.ListKind
import com.example.listacompras.domain.repository.ListRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.time.Clock

/**
 * Full pull: the server's items of every known list replace the local ones, except items that
 * still have pending operations (their local version is newer than the server's). Items missing
 * from the server are deleted locally, which also applies deletions made on another device.
 *
 * All pages are fetched before anything is written, and everything is applied in one transaction:
 * a failed fetch changes nothing (a partial snapshot would delete the items not fetched yet).
 * The pending ids are read inside that transaction so writes made during the fetch are protected.
 */
class Reconciler(
    private val api: PocketBaseApi,
    private val itemDao: ItemDao,
    private val pendingOpDao: PendingOpDao,
    private val syncMetaDao: SyncMetaDao,
    private val activeList: ActiveListProvider,
    private val listRepository: ListRepository,
    private val transactions: TransactionRunner,
    private val lock: SyncLock,
    private val tokenProvider: TokenProvider,
    private val clock: Clock,
    private val eventTypeDao: EventTypeDao,
    private val eventDao: EventDao,
    private val json: Json,
    private val log: (String) -> Unit,
    private val pageSize: Int = PAGE_SIZE,
) : FullPull {

    override suspend fun pull(): SyncOutcome = lock.mutex.withLock {
        // Logged out, the server may answer with an empty list, which would wipe the local items.
        if (tokenProvider.token().isNullOrBlank()) return@withLock SyncOutcome.AuthLost
        try {
            // Shopping must be known to pull at all. A tasks list that is not resolved yet (or does
            // not exist on the server) is not an error: it must not make every pull a "Retry".
            val missing = ListKind.entries.any { activeList.listId(it).isNullOrEmpty() }
            if (missing && !listRepository.ensureLists() && activeList.listId(ListKind.SHOPPING).isNullOrEmpty()) {
                return@withLock SyncOutcome.Retry
            }
            val listIds = activeList.knownListIds()
            if (listIds.isEmpty()) return@withLock SyncOutcome.Retry
            apply(listIds.associateWith { fetchAll(it) })
            pullCalendar()
            SyncOutcome.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            // Unlike the outbox there is nothing to discard: any failure keeps the local data and
            // is retried (a permanent 4xx will keep failing visibly rather than being skipped).
            if (classifyHttp(e.code(), tokenProvider.token() != null) == HttpVerdict.AuthLost) {
                SyncOutcome.AuthLost
            } else {
                SyncOutcome.Retry
            }
        } catch (e: IOException) {
            SyncOutcome.Retry
        } catch (e: SerializationException) {
            // A malformed response: keep the local data and try again rather than failing the work for good.
            SyncOutcome.Retry
        }
    }

    /**
     * `event_types` before `events`, each in its own try: the calendar collections must never make
     * the items pull fail (they may be missing, denied or malformed on the server). A failure leaves
     * the local calendar data as it was; the next pull tries again.
     */
    private suspend fun pullCalendar() {
        for (collection in SyncCollections.CALENDAR) {
            try {
                val records = fetchRecords(collection)
                when (collection) {
                    SyncCollections.EVENT_TYPES -> applyEventTypes(records.map { json.decodeFromJsonElement<EventTypeDto>(it) })
                    else -> applyEvents(records.map { json.decodeFromJsonElement<EventDto>(it) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                log("Calendar pull skipped ($collection): HTTP ${e.code()}")
            } catch (e: Exception) {
                log("Calendar pull skipped ($collection): ${e.javaClass.simpleName}")
            }
        }
    }

    private suspend fun fetchRecords(collection: String): List<JsonObject> {
        val all = mutableListOf<JsonObject>()
        var page = 1
        while (true) {
            val response = api.listRecords(collection, perPage = pageSize, page = page)
            all += response.items
            if (response.items.isEmpty() || page >= response.totalPages) return all
            page++
        }
    }

    private suspend fun applyEventTypes(remote: List<EventTypeDto>) {
        transactions.run {
            val pending = pendingOpDao.getPendingItemIds(SyncCollections.EVENT_TYPES).toSet()
            val remoteIds = remote.mapTo(HashSet()) { it.id }
            val stale = eventTypeDao.getAll().map { it.id }.filter { it !in remoteIds && it !in pending }
            eventTypeDao.upsertAll(remote.filter { it.id !in pending }.map { it.toEntity() })
            stale.chunked(DELETE_CHUNK).forEach { eventTypeDao.deleteByIds(it) }
        }
    }

    private suspend fun applyEvents(remote: List<EventDto>) {
        transactions.run {
            val pending = pendingOpDao.getPendingItemIds(SyncCollections.EVENTS).toSet()
            val remoteIds = remote.mapTo(HashSet()) { it.id }
            val stale = eventDao.getAll().map { it.id }.filter { it !in remoteIds && it !in pending }
            eventDao.upsertAll(remote.filter { it.id !in pending }.map { it.toEntity() })
            stale.chunked(DELETE_CHUNK).forEach { eventDao.deleteByIds(it) }
        }
    }

    private suspend fun fetchAll(listId: String): List<ItemDto> {
        val all = mutableListOf<ItemDto>()
        var page = 1
        while (true) {
            val response = api.listItems(
                filter = PocketBaseApi.listFilter(listId),
                sort = "created,id",
                perPage = pageSize,
                page = page,
            )
            all += response.items
            if (response.items.isEmpty() || page >= response.totalPages) return all
            page++
        }
    }

    /** Each list is reconciled against its own remote items only, so one list never deletes another's. */
    private suspend fun apply(remoteByList: Map<String, List<ItemDto>>) {
        transactions.run {
            val pending = pendingOpDao.getPendingItemIds(SyncCollections.ITEMS).toSet()
            for ((listId, remote) in remoteByList) {
                val remoteIds = remote.mapTo(HashSet()) { it.id }
                val staleLocal = itemDao.getItems(listId).map { it.id }.filter { it !in remoteIds && it !in pending }

                itemDao.upsertAll(remote.filter { it.id !in pending }.map { it.toEntity() })
                staleLocal.chunked(DELETE_CHUNK).forEach { itemDao.deleteByIds(it) }
            }
            syncMetaDao.put(SyncMetaEntity(LAST_FULL_SYNC_KEY, clock.millis().toString()))
        }
    }

    companion object {
        /** Epoch millis of the last successful pull; the sync status (4.4) reads it. */
        const val LAST_FULL_SYNC_KEY = "lastFullSyncAt"
        private const val PAGE_SIZE = 500
        private const val DELETE_CHUNK = 500
    }
}
