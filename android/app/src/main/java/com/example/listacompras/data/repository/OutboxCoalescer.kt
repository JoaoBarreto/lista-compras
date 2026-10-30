package com.example.listacompras.data.repository

import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.local.entity.OpType
import com.example.listacompras.data.local.entity.PendingOpEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Merges the pending operations of each item into the fewest that give the same result on the
 * server. Surviving operations are always existing rows (keeping their `opId`, so ordering holds);
 * only payloads change.
 *
 * Per item, in order:
 * - `CREATE` + `UPDATE`… → the `CREATE` with the patches merged in
 * - `CREATE` + … + `DELETE` → nothing
 * - `UPDATE`… → the first `UPDATE` with the patches merged
 * - `UPDATE`… + `DELETE` → the `DELETE`
 *
 * A `CREATE` that was already attempted (`attempts > 0`) may exist on the server even if the
 * response was lost, so it is frozen: later updates are not merged into it (a resend would be
 * rejected as a duplicate id) and a `DELETE` after it is kept (dropping both would leave a ghost
 * item on the server).
 *
 * Meant to run at the start of each sync run, before anything is sent, so no operation is in flight.
 */
class OutboxCoalescer(
    private val pendingOpDao: PendingOpDao,
    private val transactions: TransactionRunner,
    private val json: Json,
) {

    suspend fun coalesceOutbox() {
        transactions.run {
            val before = pendingOpDao.getAll()
            val after = coalesce(before).associateBy { it.opId }
            before.forEach { op ->
                val kept = after[op.opId]
                when {
                    kept == null -> pendingOpDao.delete(op.opId)
                    kept.payloadJson != op.payloadJson -> pendingOpDao.updatePayload(op.opId, kept.payloadJson)
                }
            }
        }
    }

    /** Pure transform over the whole outbox (ordered by `opId`); items are independent. */
    fun coalesce(ops: List<PendingOpEntity>): List<PendingOpEntity> =
        ops.groupBy { it.itemId }.values
            .flatMap(::coalesceItem)
            .sortedBy { it.opId }

    private fun coalesceItem(ops: List<PendingOpEntity>): List<PendingOpEntity> {
        val deleteIndex = ops.indexOfFirst { it.type == OpType.DELETE }
        val live = if (deleteIndex >= 0) ops.subList(0, deleteIndex) else ops
        val create = live.firstOrNull()?.takeIf { it.type == OpType.CREATE }
        val updates = live.filter { it.type == OpType.UPDATE }

        if (deleteIndex >= 0) {
            val delete = ops[deleteIndex]
            return when {
                create == null -> listOf(delete)
                create.attempts == 0 -> emptyList()
                else -> listOf(create, delete)
            }
        }
        return when {
            create == null -> listOfNotNull(mergeUpdates(updates))
            create.attempts == 0 -> listOf(create.withPayload(mergePayloads(create, updates, dropEmptyCheckedBy = true)))
            else -> listOfNotNull(create, mergeUpdates(updates))
        }
    }

    private fun mergeUpdates(updates: List<PendingOpEntity>): PendingOpEntity? {
        val first = updates.firstOrNull() ?: return null
        return first.withPayload(mergePayloads(first, updates.drop(1), dropEmptyCheckedBy = false))
    }

    /** Later keys win. An empty `checked_by` only means "clear" for a PATCH; a CREATE just omits it. */
    private fun mergePayloads(base: PendingOpEntity, later: List<PendingOpEntity>, dropEmptyCheckedBy: Boolean): String {
        if (later.isEmpty()) return base.payloadJson
        val merged = LinkedHashMap<String, JsonElement>()
        (listOf(base) + later).forEach { merged.putAll(parse(it.payloadJson)) }
        if (dropEmptyCheckedBy && (merged[CHECKED_BY] as? JsonPrimitive)?.content == "") merged.remove(CHECKED_BY)
        return JsonObject(merged).toString()
    }

    private fun parse(payload: String): JsonObject = json.parseToJsonElement(payload).jsonObject

    private fun PendingOpEntity.withPayload(payload: String) =
        if (payload == payloadJson) this else copy(payloadJson = payload)

    private companion object {
        const val CHECKED_BY = "checked_by"
    }
}
