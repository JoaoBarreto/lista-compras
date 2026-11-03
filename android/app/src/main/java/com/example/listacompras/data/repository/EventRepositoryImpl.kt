package com.example.listacompras.data.repository

import com.example.listacompras.data.local.dao.EventDao
import com.example.listacompras.data.local.dao.EventTypeDao
import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.local.entity.EventEntity
import com.example.listacompras.data.local.entity.OpType
import com.example.listacompras.data.local.entity.PendingOpEntity
import com.example.listacompras.data.remote.PocketBaseDates
import com.example.listacompras.data.remote.dto.EventDto
import com.example.listacompras.data.sync.SyncCollections
import com.example.listacompras.domain.model.CalendarEvent
import com.example.listacompras.domain.model.Reminder
import com.example.listacompras.domain.model.SyncState
import com.example.listacompras.domain.repository.EventRepository
import com.example.listacompras.domain.repository.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.Instant

/** Every write updates `events` and appends to `pending_ops` in one transaction; see [ItemRepositoryImpl]. */
class EventRepositoryImpl(
    private val eventDao: EventDao,
    private val eventTypeDao: EventTypeDao,
    private val pendingOpDao: PendingOpDao,
    private val transactions: TransactionRunner,
    private val currentUser: CurrentUserProvider,
    private val json: Json,
    private val clock: Clock,
    private val syncScheduler: SyncScheduler,
    private val newId: () -> String,
) : EventRepository {

    override fun observeEvents(): Flow<List<CalendarEvent>> =
        eventDao.observeAll().map { events -> events.map { it.toDomain() } }

    override suspend fun add(
        title: String,
        notes: String?,
        startsAt: Instant,
        allDay: Boolean,
        typeId: String,
        reminder: Reminder?,
    ): Boolean {
        val userId = currentUser.currentUserId() ?: return false
        val cleanTitle = title.trim()
        val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }
        if (!isValid(cleanTitle, cleanNotes)) return false
        val now = clock.millis()
        val event = EventEntity(
            id = newId(),
            title = cleanTitle,
            notes = cleanNotes,
            startsAt = startsAt.toEpochMilli(),
            allDay = allDay,
            typeId = typeId,
            reminder = reminder?.slug,
            createdBy = userId,
            createdAt = now,
            updatedAt = now,
            syncState = SyncState.PENDING,
        )
        val saved = transactions.run {
            if (eventTypeDao.getById(typeId) == null) return@run false
            eventDao.upsert(event)
            enqueue(event.id, OpType.CREATE, json.encodeToString(event.toCreateDto()), now)
            true
        }
        if (saved) syncScheduler.requestSync()
        return saved
    }

    override suspend fun update(
        id: String,
        title: String,
        notes: String?,
        startsAt: Instant,
        allDay: Boolean,
        typeId: String,
        reminder: Reminder?,
    ): Boolean {
        val cleanTitle = title.trim()
        val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }
        if (!isValid(cleanTitle, cleanNotes)) return false
        val now = clock.millis()
        val changed = transactions.run {
            val event = eventDao.getById(id) ?: return@run false
            if (eventTypeDao.getById(typeId) == null) return@run false
            eventDao.upsert(
                event.copy(
                    title = cleanTitle,
                    notes = cleanNotes,
                    startsAt = startsAt.toEpochMilli(),
                    allDay = allDay,
                    typeId = typeId,
                    reminder = reminder?.slug,
                    updatedAt = now,
                    syncState = SyncState.PENDING,
                ),
            )
            // Optional fields are sent as "" to clear them: a null would be omitted and the server would keep the old value.
            val patch = buildJsonObject {
                put("title", cleanTitle)
                put("notes", cleanNotes.orEmpty())
                put("starts_at", PocketBaseDates.format(startsAt))
                put("all_day", allDay)
                put("type", typeId)
                put("reminder", reminder?.slug.orEmpty())
            }
            enqueue(id, OpType.UPDATE, json.encodeToString(patch), now)
            true
        }
        if (changed) syncScheduler.requestSync()
        return changed
    }

    override suspend fun delete(id: String) {
        val now = clock.millis()
        val changed = transactions.run {
            if (eventDao.getById(id) == null) return@run false
            eventDao.deleteById(id)
            enqueue(id, OpType.DELETE, DELETE_PAYLOAD, now)
            true
        }
        if (changed) syncScheduler.requestSync()
    }

    private fun isValid(title: String, notes: String?) =
        title.isNotEmpty() && title.length <= CalendarLimits.TITLE_MAX &&
            (notes == null || notes.length <= CalendarLimits.NOTES_MAX)

    private suspend fun enqueue(id: String, type: OpType, payload: String, now: Long) {
        pendingOpDao.insert(
            PendingOpEntity(
                itemId = id,
                type = type,
                payloadJson = payload,
                createdAt = now,
                collection = SyncCollections.EVENTS,
            ),
        )
    }

    private companion object {
        const val DELETE_PAYLOAD = "{}"
    }
}

/** The body to POST for this event. `created`/`updated` stay null so they are omitted. */
internal fun EventEntity.toCreateDto() = EventDto(
    id = id,
    title = title,
    notes = notes,
    startsAt = Instant.ofEpochMilli(startsAt),
    allDay = allDay,
    type = typeId,
    reminder = reminder,
    createdBy = createdBy,
)
