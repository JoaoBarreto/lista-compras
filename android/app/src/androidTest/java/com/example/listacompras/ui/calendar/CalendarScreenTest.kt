package com.example.listacompras.ui.calendar

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.listacompras.R
import com.example.listacompras.data.local.AppDatabase
import com.example.listacompras.data.local.entity.EventTypeEntity
import com.example.listacompras.data.local.entity.OpType
import com.example.listacompras.data.remote.PocketBaseApiFactory
import com.example.listacompras.data.repository.CurrentUserProvider
import com.example.listacompras.data.repository.EventRepositoryImpl
import com.example.listacompras.data.repository.EventTypeRepositoryImpl
import com.example.listacompras.data.repository.RoomTransactionRunner
import com.example.listacompras.data.sync.SyncCollections
import com.example.listacompras.domain.model.SyncState
import com.example.listacompras.domain.model.SyncStatus
import com.example.listacompras.domain.repository.SyncScheduler
import com.example.listacompras.domain.repository.SyncStatusProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Drives the real calendar screen over an in-memory database and the real repositories. There is no
 * server, login or network: everything the user does is only written locally and queued in
 * `pending_ops`, which is exactly what offline means.
 */
class CalendarScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("Europe/Lisbon")
    private val clock = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), zone)
    private lateinit var db: AppDatabase
    private lateinit var events: EventRepositoryImpl
    private lateinit var types: EventTypeRepositoryImpl
    private var counter = 0

    private fun text(id: Int) = context.getString(id)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val tx = RoomTransactionRunner(db)
        val scheduler = object : SyncScheduler { override fun requestSync() = Unit }
        val ids = { "id" + (++counter).toString().padStart(13, '0') }
        types = EventTypeRepositoryImpl(db.eventTypeDao(), db.eventDao(), db.pendingOpDao(), tx, PocketBaseApiFactory.json, clock, scheduler, ids)
        events = EventRepositoryImpl(
            db.eventDao(), db.eventTypeDao(), db.pendingOpDao(), tx,
            object : CurrentUserProvider { override fun currentUserId() = "user1" },
            PocketBaseApiFactory.json, clock, scheduler, ids,
        )
        runBlocking {
            db.eventTypeDao().upsert(EventTypeEntity("t1", "Família", "#4CAF50", 1, 1, SyncState.SYNCED))
        }
        val viewModel = CalendarViewModel(
            events, types,
            object : SyncStatusProvider {
                override val status: Flow<SyncStatus> = flowOf(SyncStatus.Synced)
                override suspend fun acknowledgeErrors() = Unit
            },
            clock,
        )
        rule.setContent {
            val state by viewModel.uiState.collectAsState()
            CalendarScreen(
                state = state,
                tabs = {},
                onOpenSettings = {},
                onErrorAcknowledged = {},
                onPreviousMonth = viewModel::onPreviousMonth,
                onNextMonth = viewModel::onNextMonth,
                onToday = viewModel::onToday,
                onSaveEvent = viewModel::onSaveEvent,
                onDeleteEvent = viewModel::onDeleteEvent,
                onSaveType = viewModel::onSaveType,
                onDeleteType = viewModel::onDeleteType,
            )
        }
    }

    @After
    fun tearDown() = db.close()

    private fun seedEvent(title: String, day: Int = 20, hour: Int = 20) = runBlocking {
        val startsAt = CalendarDates.toInstant(LocalDate.of(2026, 11, day), LocalTime.of(hour, 0), false, zone)
        assertTrue(events.add(title, null, startsAt, false, "t1", null))
    }

    private fun waitForText(value: String) = rule.waitUntil(TIMEOUT) {
        rule.onAllNodes(androidx.compose.ui.test.hasText(value)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForNoText(value: String) = rule.waitUntil(TIMEOUT) {
        rule.onAllNodes(androidx.compose.ui.test.hasText(value)).fetchSemanticsNodes().isEmpty()
    }

    private fun ops() = runBlocking { db.pendingOpDao().getAll() }

    @Test
    fun creatingAnEventWritesItLocallyAndQueuesACreate() {
        rule.onNodeWithText(text(R.string.calendar_new_event)).performClick()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Jantar")
        rule.onNodeWithText(text(R.string.event_save)).performClick()

        waitForText("Jantar")
        val stored = runBlocking { db.eventDao().getAll() }.single()
        assertEquals("Jantar", stored.title)
        assertEquals(SyncState.PENDING, stored.syncState)
        val op = ops().single()
        assertEquals(SyncCollections.EVENTS, op.collection)
        assertEquals(OpType.CREATE, op.type)
    }

    @Test
    fun editingAnEventUpdatesTheTitleAndQueuesAnUpdate() {
        seedEvent("Jantar")
        waitForText("Jantar")

        rule.onNodeWithText("Jantar").performClick()
        rule.onAllNodes(hasSetTextAction())[0].performTextReplacement("Almoço")
        rule.onNodeWithText(text(R.string.event_save)).performClick()

        waitForText("Almoço")
        assertEquals("Almoço", runBlocking { db.eventDao().getAll() }.single().title)
        assertEquals(listOf(OpType.CREATE, OpType.UPDATE), ops().map { it.type })
    }

    @Test
    fun deletingAnEventRemovesItAndQueuesADelete() {
        seedEvent("Jantar")
        waitForText("Jantar")

        rule.onNodeWithText("Jantar").performClick()
        rule.onNodeWithText(text(R.string.event_delete)).performClick()

        waitForNoText("Jantar")
        assertTrue(runBlocking { db.eventDao().getAll() }.isEmpty())
        // A CREATE that was never sent and its DELETE are both queued; the sync engine merges them later.
        assertEquals(OpType.DELETE, ops().last().type)
    }

    @Test
    fun aTypeInUseCannotBeDeletedAndShowsTheWarning() {
        seedEvent("Jantar")
        waitForText("Jantar")

        rule.onNodeWithText(text(R.string.calendar_manage_types)).performClick()
        rule.onNodeWithText(text(R.string.event_delete)).performClick()

        waitForText(text(R.string.types_in_use))
        assertEquals(1, runBlocking { db.eventTypeDao().getAll() }.size)
    }

    @Test
    fun aNewTypeIsStoredAndQueued() {
        rule.onNodeWithText(text(R.string.calendar_manage_types)).performClick()
        rule.onNodeWithText(text(R.string.types_new)).performClick()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Trabalho")
        rule.onNodeWithText(text(R.string.event_save)).performClick()

        waitForText("Trabalho")
        assertEquals(setOf("Família", "Trabalho"), runBlocking { db.eventTypeDao().getAll() }.map { it.name }.toSet())
        val op = ops().single()
        assertEquals(SyncCollections.EVENT_TYPES, op.collection)
        assertEquals(OpType.CREATE, op.type)
    }

    @Test
    fun theEventRowReadsTitleFullDateTimeAndTypeForTalkBack() {
        seedEvent("Jantar", day = 20, hour = 20)
        waitForText("Jantar")

        val expected = context.getString(
            R.string.calendar_event_description,
            "Jantar",
            CalendarFormats.fullDate(LocalDate.of(2026, 11, 20)),
            CalendarFormats.time(LocalTime.of(20, 0)),
            "Família",
        )
        rule.onNodeWithContentDescription(expected).assertExists()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
