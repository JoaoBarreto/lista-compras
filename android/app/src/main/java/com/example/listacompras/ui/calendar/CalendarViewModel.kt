package com.example.listacompras.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.listacompras.domain.model.SyncStatus
import com.example.listacompras.domain.repository.EventRepository
import com.example.listacompras.domain.repository.EventTypeRepository
import com.example.listacompras.domain.repository.SyncStatusProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** One [CalendarUiState] for the visible month; UI events come in as functions. Reads never wait for the network. */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val typeRepository: EventTypeRepository,
    private val syncStatusProvider: SyncStatusProvider,
    private val clock: Clock,
) : ViewModel() {

    // The single source of truth: the month shown is the month of the selected day.
    private val selected = MutableStateFlow(LocalDate.now(clock))

    val uiState: StateFlow<CalendarUiState> = combine(
        eventRepository.observeEvents(),
        typeRepository.observeTypes(),
        // Events must not wait for the first status (it depends on network callbacks).
        syncStatusProvider.status.onStart { emit(SyncStatus.Syncing) },
        selected,
    ) { events, types, status, selectedDate ->
        val visibleMonth = YearMonth.from(selectedDate)
        CalendarUiState(
            month = visibleMonth,
            today = LocalDate.now(clock),
            selectedDate = selectedDate,
            zone = clock.zone,
            days = CalendarDates.groupByDay(events, visibleMonth, clock.zone),
            types = types,
            syncStatus = status,
            isLoading = false,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        LocalDate.now(clock).let { today ->
            CalendarUiState(month = YearMonth.from(today), today = today, selectedDate = today, zone = clock.zone)
        },
    )

    /** Moves the selected day by a month; a day that does not exist there is clamped (31 Jan → 28 Feb). */
    fun onPreviousMonth() = selected.update { it.minusMonths(1) }

    fun onNextMonth() = selected.update { it.plusMonths(1) }

    fun onToday() { selected.value = LocalDate.now(clock) }

    fun onSelectDate(date: LocalDate) { selected.value = date }

    /** [id] null creates. [onResult] gets false when the repository refused, so the editor can stay open. */
    fun onSaveEvent(id: String?, draft: EventDraft, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val startsAt = CalendarDates.toInstant(draft.date, draft.time, draft.allDay, clock.zone)
            val notes = draft.notes.ifBlank { null }
            val saved = if (id == null) {
                eventRepository.add(draft.title, notes, startsAt, draft.allDay, draft.typeId, draft.reminder)
            } else {
                eventRepository.update(id, draft.title, notes, startsAt, draft.allDay, draft.typeId, draft.reminder)
            }
            onResult(saved)
        }
    }

    fun onDeleteEvent(id: String) {
        viewModelScope.launch { eventRepository.delete(id) }
    }

    fun onSaveType(id: String?, name: String, color: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(if (id == null) typeRepository.add(name, color) else typeRepository.update(id, name, color))
        }
    }

    /** [onResult] gets false when the type is still used by an event. */
    fun onDeleteType(id: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(typeRepository.delete(id)) }
    }

    fun onErrorAcknowledged() {
        viewModelScope.launch { syncStatusProvider.acknowledgeErrors() }
    }
}
