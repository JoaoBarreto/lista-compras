package com.example.listacompras.domain.model

import java.time.Instant

data class CalendarEvent(
    val id: String,
    val title: String,
    val notes: String?,
    val startsAt: Instant,
    val allDay: Boolean,
    val typeId: String,
    val reminder: Reminder?,
    val createdBy: String,
    val updatedAt: Instant,
    val syncState: SyncState,
)
