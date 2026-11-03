package com.example.listacompras.data.repository

import com.example.listacompras.data.local.dao.EventDao
import com.example.listacompras.data.local.dao.EventTypeDao
import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.local.entity.EventTypeEntity
import com.example.listacompras.data.local.entity.OpType
import com.example.listacompras.data.local.entity.PendingOpEntity
import com.example.listacompras.data.remote.dto.EventTypeDto
import com.example.listacompras.data.sync.SyncCollections
import com.example.listacompras.domain.model.EventType
import com.example.listacompras.domain.model.SyncState
import com.example.listacompras.domain.repository.EventTypeRepository
import com.example.listacompras.domain.repository.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Clock

/** Every write updates `event_types` and appends to `pending_ops` in one transaction; see [ItemRepositoryImpl]. */
class EventTypeRepositoryImpl(
    private val eventTypeDao: EventTypeDao,
    private val eventDao: EventDao,
    private val pendingOpDao: PendingOpDao,
    private val transactions: TransactionRunner,
    private val json: Json,
    private val clock: Clock,
    private val syncScheduler: SyncScheduler,
    private val newId: () -> String,
) : EventTypeRepository {

    override fun observeTypes(): Flow<List<EventType>> =
        eventTypeDao.observeAll().map { types -> types.map { it.toDomain() } }

    override suspend fun add(name: String, color: String): Boolean {
        val cleanName = name.trim()
        if (!isValid(cleanName, color)) return false
        val now = clock.millis()
        val type = EventTypeEntity(
            id = newId(),
            name = cleanName,
            color = color,
            createdAt = now,
            updatedAt = now,
            syncState = SyncState.PENDING,
        )
        transactions.run {
            eventTypeDao.upsert(type)
            enqueue(type.id, OpType.CREATE, json.encodeToString(EventTypeDto(type.id, type.name, type.color)), now)
        }
        syncScheduler.requestSync()
        return true
    }

    override suspend fun update(id: String, name: String, color: String): Boolean {
        val cleanName = name.trim()
        if (!isValid(cleanName, color)) return false
        val now = clock.millis()
        val changed = transactions.run {
            val type = eventTypeDao.getById(id) ?: return@run false
            eventTypeDao.upsert(type.copy(name = cleanName, color = color, updatedAt = now, syncState = SyncState.PENDING))
            val patch = buildJsonObject {
                put("name", cleanName)
                put("color", color)
            }
            enqueue(id, OpType.UPDATE, json.encodeToString(patch), now)
            true
        }
        if (changed) syncScheduler.requestSync()
        return changed
    }

    override suspend fun delete(id: String): Boolean {
        val now = clock.millis()
        val changed = transactions.run {
            if (eventTypeDao.getById(id) == null || eventDao.countByType(id) > 0) return@run false
            eventTypeDao.deleteById(id)
            enqueue(id, OpType.DELETE, DELETE_PAYLOAD, now)
            true
        }
        if (changed) syncScheduler.requestSync()
        return changed
    }

    private fun isValid(name: String, color: String) =
        name.isNotEmpty() && name.length <= CalendarLimits.TYPE_NAME_MAX && CalendarLimits.isColor(color)

    private suspend fun enqueue(id: String, type: OpType, payload: String, now: Long) {
        pendingOpDao.insert(
            PendingOpEntity(
                itemId = id,
                type = type,
                payloadJson = payload,
                createdAt = now,
                collection = SyncCollections.EVENT_TYPES,
            ),
        )
    }

    private companion object {
        const val DELETE_PAYLOAD = "{}"
    }
}
