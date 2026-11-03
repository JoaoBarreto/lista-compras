package com.example.listacompras.ui.calendar

import com.example.listacompras.domain.model.CalendarEvent
import com.example.listacompras.domain.model.EventType
import com.example.listacompras.domain.model.Reminder
import com.example.listacompras.domain.model.SyncState
import com.example.listacompras.domain.model.SyncStatus
import com.example.listacompras.domain.repository.EventRepository
import com.example.listacompras.domain.repository.EventTypeRepository
import com.example.listacompras.domain.repository.SyncStatusProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val zone = ZoneId.of("Europe/Lisbon")
    private val clock = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), zone)
    private val events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    private val types = MutableStateFlow<List<EventType>>(emptyList())
    private val statusFlow = MutableStateFlow<SyncStatus?>(null)
    private val added = mutableListOf<Triple<String, Instant, Boolean>>()
    private var accept = true
    private var acknowledged = 0

    private val eventRepository = object : EventRepository {
        override fun observeEvents(): Flow<List<CalendarEvent>> = events
        override suspend fun add(title: String, notes: String?, startsAt: Instant, allDay: Boolean, typeId: String, reminder: Reminder?): Boolean {
            added += Triple(title, startsAt, allDay)
            return accept
        }
        override suspend fun update(id: String, title: String, notes: String?, startsAt: Instant, allDay: Boolean, typeId: String, reminder: Reminder?) = accept
        override suspend fun delete(id: String) { events.value = events.value.filterNot { it.id == id } }
    }
    private val typeRepository = object : EventTypeRepository {
        override fun observeTypes(): Flow<List<EventType>> = types
        override suspend fun add(name: String, color: String) = accept
        override suspend fun update(id: String, name: String, color: String) = accept
        override suspend fun delete(id: String) = accept
    }
    private val syncStatus = object : SyncStatusProvider {
        // Never emits until a test sets it: the screen must not wait for the first status.
        override val status: Flow<SyncStatus> = flow { statusFlow.collect { if (it != null) emit(it) } }
        override suspend fun acknowledgeErrors() { acknowledged++ }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = CalendarViewModel(eventRepository, typeRepository, syncStatus, clock)

    private fun event(id: String, date: LocalDate) = CalendarEvent(
        id, id, null, CalendarDates.toInstant(date, LocalTime.NOON, false, zone), false, "t1", null, "u", Instant.EPOCH, SyncState.SYNCED,
    )

    @Test
    fun `local events show without waiting for the sync status or the network`() = runTest {
        events.value = listOf(event("e1", LocalDate.of(2026, 11, 20)))
        val vm = viewModel()

        val state = vm.uiState.first { !it.isLoading }

        assertEquals(YearMonth.of(2026, 11), state.month)
        assertEquals(LocalDate.of(2026, 11, 15), state.today)
        assertEquals(listOf("e1"), state.days.single().events.map { it.id })
        assertEquals(SyncStatus.Syncing, state.syncStatus)
    }

    @Test
    fun `month navigation filters the events and today returns to the current month`() = runTest {
        events.value = listOf(event("nov", LocalDate.of(2026, 11, 20)), event("dec", LocalDate.of(2026, 12, 3)))
        val vm = viewModel()

        vm.onNextMonth()
        assertEquals(listOf("dec"), vm.uiState.first { it.month == YearMonth.of(2026, 12) }.days.flatMap { it.events }.map { it.id })
        vm.onPreviousMonth(); vm.onPreviousMonth()
        assertTrue(vm.uiState.first { it.month == YearMonth.of(2026, 10) }.days.isEmpty())
        vm.onToday()
        assertEquals(YearMonth.of(2026, 11), vm.uiState.first { it.month == YearMonth.of(2026, 11) }.month)
    }

    @Test
    fun `saving a new event converts the draft with the clock zone and reports the result`() = runTest {
        val vm = viewModel()
        val draft = EventDraft("Jantar", " ", LocalDate.of(2026, 12, 1), LocalTime.of(20, 0), false, "t1", Reminder.ONE_DAY)
        var result: Boolean? = null

        vm.onSaveEvent(null, draft) { result = it }

        assertEquals(true, result)
        assertEquals(Triple("Jantar", Instant.parse("2026-12-01T20:00:00Z"), false), added.single())
        accept = false
        vm.onSaveEvent(null, draft) { result = it }
        assertEquals(false, result)
    }

    @Test
    fun `an all-day draft is stored at midnight UTC`() = runTest {
        val vm = viewModel()
        vm.onSaveEvent(null, EventDraft("Férias", "", LocalDate.of(2026, 12, 1), LocalTime.of(20, 0), true, "t1", null)) {}
        assertEquals(Instant.parse("2026-12-01T00:00:00Z"), added.single().second)
    }

    @Test
    fun `deleting an event and a refused type delete are reported`() = runTest {
        events.value = listOf(event("e1", LocalDate.of(2026, 11, 20)))
        val vm = viewModel()
        vm.onDeleteEvent("e1")
        assertTrue(vm.uiState.first { !it.isLoading }.days.isEmpty())

        accept = false
        var deleted: Boolean? = null
        vm.onDeleteType("t1") { deleted = it }
        assertFalse(deleted!!)
    }

    @Test
    fun `acknowledging errors reaches the provider and types are exposed`() = runTest {
        types.value = listOf(EventType("t1", "Família", "#4CAF50", SyncState.SYNCED))
        val vm = viewModel()
        vm.onErrorAcknowledged()
        assertEquals(1, acknowledged)
        assertEquals("Família", vm.uiState.first { it.types.isNotEmpty() }.types.single().name)
    }

    @Test
    fun `selecting a day sets the selected date and the month follows it`() = runTest {
        val vm = viewModel()
        assertEquals(LocalDate.of(2026, 11, 15), vm.uiState.first { !it.isLoading }.selectedDate)

        vm.onSelectDate(LocalDate.of(2026, 12, 24))

        val state = vm.uiState.first { it.selectedDate == LocalDate.of(2026, 12, 24) }
        assertEquals(YearMonth.of(2026, 12), state.month)
    }

    @Test
    fun `changing month clamps the selected day, as java time does (31 Jan to 28 Feb)`() = runTest {
        val vm = viewModel()
        vm.onSelectDate(LocalDate.of(2027, 1, 31))

        vm.onNextMonth()
        assertEquals(LocalDate.of(2027, 2, 28), vm.uiState.first { it.month == YearMonth.of(2027, 2) }.selectedDate)
        vm.onNextMonth()
        assertEquals(LocalDate.of(2027, 3, 28), vm.uiState.first { it.month == YearMonth.of(2027, 3) }.selectedDate)
    }

    @Test
    fun `today selects the current day again`() = runTest {
        val vm = viewModel()
        vm.onSelectDate(LocalDate.of(2027, 5, 3))
        vm.onToday()
        assertEquals(LocalDate.of(2026, 11, 15), vm.uiState.first { it.selectedDate == LocalDate.of(2026, 11, 15) }.selectedDate)
    }
}
