package com.example.listacompras.domain.repository

import kotlinx.coroutines.flow.Flow

/** Whether to notify about calendar events the other person created. Stored locally; on by default. */
interface EventNotificationSettings {
    val eventsEnabled: Flow<Boolean>

    suspend fun setEventsEnabled(enabled: Boolean)
}
