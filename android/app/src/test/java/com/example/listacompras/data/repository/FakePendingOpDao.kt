package com.example.listacompras.data.repository

import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.local.entity.PendingOpEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePendingOpDao : PendingOpDao {
    val ops = mutableListOf<PendingOpEntity>()
    private var lastId = 0L
    override suspend fun insert(op: PendingOpEntity): Long {
        val id = ++lastId
        ops += op.copy(opId = id)
        return id
    }
    override suspend fun getAll() = ops.toList()
    override suspend fun getForItem(itemId: String) = ops.filter { it.itemId == itemId }
    override suspend fun delete(opId: Long) { ops.removeAll { it.opId == opId } }
    override suspend fun deleteForItem(itemId: String) { ops.removeAll { it.itemId == itemId } }
    override suspend fun incrementAttempts(opId: Long) = Unit
    override suspend fun updatePayload(opId: Long, payloadJson: String) {
        ops.replaceAll { if (it.opId == opId) it.copy(payloadJson = payloadJson) else it }
    }
    override fun observeCount(): Flow<Int> = MutableStateFlow(ops.size)
    override suspend fun getPendingItemIds() = ops.map { it.itemId }.distinct()
}
