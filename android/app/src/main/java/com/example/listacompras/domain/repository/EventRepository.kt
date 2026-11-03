package com.example.listacompras.domain.repository

import com.example.listacompras.domain.model.CalendarEvent
import com.example.listacompras.domain.model.Reminder
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** The UI's only view of the events: reads come from local storage, writes are queued for sync. */
interface EventRepository {

    /** All events, earliest first. */
    fun observeEvents(): Flow<List<CalendarEvent>>

    /**
     * Adds an event. Returns false (and changes nothing) when there is no logged-in user, the title
     * is blank or too long, the notes are too long, or [typeId] is not a known type.
     */
    suspend fun add(
        title: String,
        notes: String?,
        startsAt: Instant,
        allDay: Boolean,
        typeId: String,
        reminder: Reminder?,
    ): Boolean

    /** Same validation as [add]; also false when the event does not exist. */
    suspend fun update(
        id: String,
        title: String,
        notes: String?,
        startsAt: Instant,
        allDay: Boolean,
        typeId: String,
        reminder: Reminder?,
    ): Boolean

    suspend fun delete(id: String)
}
