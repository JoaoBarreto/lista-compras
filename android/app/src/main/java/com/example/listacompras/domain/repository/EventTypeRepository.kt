package com.example.listacompras.domain.repository

import com.example.listacompras.domain.model.EventType
import kotlinx.coroutines.flow.Flow

/** The UI's only view of the event types: reads come from local storage, writes are queued for sync. */
interface EventTypeRepository {

    fun observeTypes(): Flow<List<EventType>>

    /** Returns false (and changes nothing) for a blank or too long name, or a colour that is not `#RRGGBB`. */
    suspend fun add(name: String, color: String): Boolean

    /** Same validation as [add]; also false when the type does not exist. */
    suspend fun update(id: String, name: String, color: String): Boolean

    /** Returns false (and changes nothing) when the type is unknown or still used by an event. */
    suspend fun delete(id: String): Boolean
}
