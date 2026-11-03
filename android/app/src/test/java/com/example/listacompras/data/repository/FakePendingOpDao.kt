package com.example.listacompras.data.repository

import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.local.entity.PendingOpEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePendingOpDao : PendingOpDao {
    val ops = mutableListOf<PendingOpEntity>()
    private var lastId = 0L

    /** Bumped by every mutation so [observeCount] is live, not a snapshot. */
    private val changes = MutableStateFlow(0)
    private fun changed() { changes.value++ }
    override suspend fun insert(op: PendingOpEntity): Long {
        val id = ++lastId
        ops += op.copy(opId = id)
        changed()
        return id
    }
    override suspend fun getAll() = ops.toList()
    override suspend fun getForItem(collection: String, itemId: String) = ops.filter { it.collection == collection && it.itemId == itemId }
    override suspend fun delete(opId: Long) { ops.removeAll { it.opId == opId }; changed() }
    override suspend fun deleteForItem(collection: String, itemId: String) { ops.removeAll { it.collection == collection && it.itemId == itemId }; changed() }
    override suspend fun incrementAttempts(opId: Long) {
        ops.replaceAll { if (it.opId == opId) it.copy(attempts = it.attempts + 1) else it }
    }
    override suspend fun updatePayload(opId: Long, payloadJson: String) {
        ops.replaceAll { if (it.opId == opId) it.copy(payloadJson = payloadJson) else it }
    }
    override fun observeCount(): Flow<Int> = changes.map { ops.size }
    override suspend fun getPendingItemIds(collection: String) = ops.filter { it.collection == collection }.map { it.itemId }.distinct()
}
