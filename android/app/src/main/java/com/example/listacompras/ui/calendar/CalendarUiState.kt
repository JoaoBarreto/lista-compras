package com.example.listacompras.ui.calendar

import com.example.listacompras.domain.model.EventType
import com.example.listacompras.domain.model.Reminder
import com.example.listacompras.domain.model.SyncStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

data class CalendarUiState(
    val month: YearMonth,
    val today: LocalDate,
    val zone: ZoneId,
    val days: List<DayGroup> = emptyList(),
    val types: List<EventType> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.Syncing,
    /** True until the local database has emitted once, so the empty message does not flash. */
    val isLoading: Boolean = true,
)

/** What the editor dialog hands back; [time] is ignored for all-day events. */
data class EventDraft(
    val title: String,
    val notes: String,
    val date: LocalDate,
    val time: LocalTime,
    val allDay: Boolean,
    val typeId: String,
    val reminder: Reminder?,
)
