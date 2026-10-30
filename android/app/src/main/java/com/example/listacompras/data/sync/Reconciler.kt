package com.example.listacompras.data.sync

import com.example.listacompras.data.local.dao.ItemDao
import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.local.entity.SyncMetaEntity
import com.example.listacompras.data.remote.PocketBaseApi
import com.example.listacompras.data.remote.TokenProvider
import com.example.listacompras.data.remote.dto.ItemDto
import com.example.listacompras.data.repository.ActiveListProvider
import com.example.listacompras.data.repository.TransactionRunner
import com.example.listacompras.data.repository.toEntity
import com.example.listacompras.domain.repository.ListRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.time.Clock

/**
 * Full pull: the server's items of the active list replace the local ones, except items that
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
    private val pageSize: Int = PAGE_SIZE,
) {

    suspend fun pull(): SyncOutcome = lock.mutex.withLock {
        try {
            val listId = activeList.listId()?.takeIf { it.isNotEmpty() }
                ?: run {
                    if (!listRepository.ensureActiveList()) return@withLock SyncOutcome.Retry
                    activeList.listId()?.takeIf { it.isNotEmpty() } ?: return@withLock SyncOutcome.Retry
                }
            apply(listId, fetchAll(listId))
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

    private suspend fun apply(listId: String, remote: List<ItemDto>) {
        transactions.run {
            val pending = pendingOpDao.getPendingItemIds().toSet()
            val remoteIds = remote.mapTo(HashSet()) { it.id }
            val staleLocal = itemDao.getItems(listId).map { it.id }.filter { it !in remoteIds && it !in pending }

            itemDao.upsertAll(remote.filter { it.id !in pending }.map { it.toEntity() })
            staleLocal.chunked(DELETE_CHUNK).forEach { itemDao.deleteByIds(it) }
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
