package com.example.listacompras.data.repository

import com.example.listacompras.data.local.dao.ItemDao
import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.local.entity.ItemEntity
import com.example.listacompras.data.local.entity.OpType
import com.example.listacompras.data.local.entity.PendingOpEntity
import com.example.listacompras.data.remote.dto.CheckedPatch
import com.example.listacompras.domain.model.Category
import com.example.listacompras.domain.model.ShoppingItem
import com.example.listacompras.domain.model.SyncState
import com.example.listacompras.domain.repository.ItemRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Clock

/**
 * Every write updates `items` and appends to `pending_ops` in one transaction. Operations are
 * not coalesced here (see OutboxCoalescer) and nothing is sent: that is the sync worker's job.
 * `payloadJson` is exactly the HTTP body to send.
 */
class ItemRepositoryImpl(
    private val itemDao: ItemDao,
    private val pendingOpDao: PendingOpDao,
    private val transactions: TransactionRunner,
    private val activeList: ActiveListProvider,
    private val currentUser: CurrentUserProvider,
    private val json: Json,
    private val clock: Clock,
    private val newId: () -> String,
) : ItemRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeItems(): Flow<List<ShoppingItem>> =
        activeList.observeListId().flatMapLatest { listId ->
            if (listId.isNullOrEmpty()) flowOf(emptyList())
            else itemDao.observeItems(listId).map { items -> items.map { it.toDomain() } }
        }

    override suspend fun add(name: String, quantity: String?, category: Category?): Boolean {
        val listId = activeList.listId()?.takeIf { it.isNotEmpty() } ?: return false
        val userId = currentUser.currentUserId() ?: return false
        val now = clock.millis()
        val item = ItemEntity(
            id = newId(),
            listId = listId,
            name = name.trim(),
            quantity = quantity?.trim()?.takeIf { it.isNotEmpty() },
            checked = false,
            category = category?.slug,
            addedBy = userId,
            checkedBy = null,
            createdAt = now,
            updatedAt = now,
            syncState = SyncState.PENDING,
        )
        transactions.run {
            itemDao.upsert(item)
            enqueue(item.id, OpType.CREATE, json.encodeToString(item.toCreateDto()), now)
        }
        return true
    }

    override suspend fun setChecked(id: String, checked: Boolean) {
        val userId = currentUser.currentUserId() ?: return
        val now = clock.millis()
        transactions.run {
            val item = itemDao.getById(id) ?: return@run
            itemDao.upsert(
                item.copy(
                    checked = checked,
                    checkedBy = if (checked) userId else null,
                    updatedAt = now,
                    syncState = SyncState.PENDING,
                ),
            )
            val patch = CheckedPatch(checked, if (checked) userId else "")
            enqueue(id, OpType.UPDATE, json.encodeToString(patch), now)
        }
    }

    override suspend fun delete(id: String) {
        val now = clock.millis()
        transactions.run {
            if (itemDao.getById(id) == null) return@run
            itemDao.deleteById(id)
            enqueue(id, OpType.DELETE, DELETE_PAYLOAD, now)
        }
    }

    override suspend fun clearChecked() {
        val listId = activeList.listId()?.takeIf { it.isNotEmpty() } ?: return
        val now = clock.millis()
        transactions.run {
            val ids = itemDao.getCheckedIds(listId)
            if (ids.isEmpty()) return@run
            itemDao.deleteByIds(ids)
            ids.forEach { enqueue(it, OpType.DELETE, DELETE_PAYLOAD, now) }
        }
    }

    private suspend fun enqueue(itemId: String, type: OpType, payload: String, now: Long) {
        pendingOpDao.insert(PendingOpEntity(itemId = itemId, type = type, payloadJson = payload, createdAt = now))
    }

    private companion object {
        /** DELETE has no body; the item id lives in the op row. */
        const val DELETE_PAYLOAD = "{}"
    }
}
