package com.example.listacompras.data.reminder

import com.example.listacompras.data.local.entity.EventEntity
import com.example.listacompras.data.local.entity.EventTypeEntity
import com.example.listacompras.data.local.entity.SyncMetaEntity
import com.example.listacompras.data.repository.FakeEventDao
import com.example.listacompras.data.repository.FakeEventTypeDao
import com.example.listacompras.data.repository.FakeSyncMetaDao
import com.example.listacompras.domain.model.SyncState
import com.example.listacompras.domain.reminder.DueReminder
import com.example.listacompras.domain.reminder.ReminderAlarms
import com.example.listacompras.domain.reminder.ReminderNotifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSchedulerTest {

    private val eventDao = FakeEventDao()
    private val typeDao = FakeEventTypeDao()
    private val meta = FakeSyncMetaDao()
    private var now = Instant.parse("2026-12-01T12:00:00Z")
    private var zone = ZoneId.of("Europe/Lisbon")
    private val alarm = object : ReminderAlarms {
        var at: Instant? = null
        var cancels = 0
        override fun scheduleAt(at: Instant) { this.at = at }
        override fun cancel() { at = null; cancels++ }
    }
    private val shown = mutableListOf<List<DueReminder>>()
    private val notifier = object : ReminderNotifier {
        override fun show(reminders: List<DueReminder>) { shown += reminders }
    }
    private val scheduler = ReminderScheduler(eventDao, typeDao, meta, alarm, notifier, now = { now }, zone = { zone })

    private fun event(id: String, startsAt: String, reminder: String?, allDay: Boolean = false, createdBy: String = "u") =
        EventEntity(id, id, null, Instant.parse(startsAt).toEpochMilli(), allDay, "t1", reminder, createdBy, 1, 1, SyncState.SYNCED)

    @Test
    fun `an event from the other user is scheduled like any other`() = runTest {
        eventDao.upsert(event("e1", "2026-12-05T20:00:00Z", "1h", createdBy = "other"))

        scheduler.reschedule()

        assertEquals(Instant.parse("2026-12-05T19:00:00Z"), alarm.at)
    }

    @Test
    fun `events without a reminder schedule nothing and deleting the last one cancels the alarm`() = runTest {
        eventDao.upsert(event("none", "2026-12-05T20:00:00Z", null))
        scheduler.reschedule()
        assertNull(alarm.at)

        eventDao.upsert(event("e1", "2026-12-05T20:00:00Z", "at_time"))
        scheduler.reschedule()
        assertEquals(Instant.parse("2026-12-05T20:00:00Z"), alarm.at)

        eventDao.deleteById("e1")
        scheduler.reschedule()
        assertNull(alarm.at)
    }

    @Test
    fun `the observer reschedules on every change of the events`() = runTest {
        scheduler.observe(backgroundScope + UnconfinedTestDispatcher(testScheduler))

        eventDao.upsert(event("e1", "2026-12-05T20:00:00Z", "at_time"))
        assertEquals(Instant.parse("2026-12-05T20:00:00Z"), alarm.at)
        eventDao.upsertAll(listOf(event("e2", "2026-12-03T08:00:00Z", "at_time")))
        assertEquals(Instant.parse("2026-12-03T08:00:00Z"), alarm.at)
        eventDao.deleteByIds(listOf("e1", "e2"))
        assertNull(alarm.at)
    }

    @Test
    fun `firing shows the due events with their type name, advances and schedules the next`() = runTest {
        typeDao.upsert(EventTypeEntity("t1", "Família", "#4CAF50", 1, 1, SyncState.SYNCED))
        scheduler.reschedule() // creates the watermark at "now"
        eventDao.upsert(event("a", "2026-12-01T20:00:00Z", "at_time"))
        eventDao.upsert(event("b", "2026-12-09T20:00:00Z", "1d"))
        now = Instant.parse("2026-12-01T20:00:30Z") // the alarm arrived 30 s late

        scheduler.fireDue()

        assertEquals(listOf("a"), shown.single().map { it.event.id })
        assertEquals("Família", shown.single().single().typeName)
        assertEquals(Instant.parse("2026-12-08T20:00:00Z"), alarm.at)

        scheduler.fireDue() // the same alarm firing twice must not repeat the reminder
        assertEquals(1, shown.size)
    }

    @Test
    fun `a late reschedule between the due time and the delivery does not lose the reminder`() = runTest {
        scheduler.reschedule()
        eventDao.upsert(event("a", "2026-12-01T20:00:00Z", "at_time"))
        eventDao.upsert(event("later", "2026-12-08T20:00:00Z", "at_time"))
        scheduler.reschedule()
        now = Instant.parse("2026-12-01T20:00:30Z") // Doze delayed the alarm; a sync touches the database meanwhile

        scheduler.reschedule()

        assertEquals(Instant.parse("2026-12-01T20:00:00Z"), alarm.at) // still in the past: fires immediately
        scheduler.fireDue()
        assertEquals(listOf("a"), shown.single().map { it.event.id })
        assertEquals(Instant.parse("2026-12-08T20:00:00Z"), alarm.at)
    }

    @Test
    fun `installing does not replay reminders that fired before the first run`() = runTest {
        eventDao.upsert(event("old", "2026-12-01T11:00:00Z", "at_time"))

        scheduler.fireDue()

        assertTrue(shown.isEmpty())
        assertNull(alarm.at)
    }

    @Test
    fun `an event whose reminder time already passed notifies at once if the last run was before it`() = runTest {
        meta.put(SyncMetaEntity("reminderWatermark", Instant.parse("2026-12-01T10:00:00Z").toEpochMilli().toString()))
        eventDao.upsert(event("soon", "2026-12-01T12:30:00Z", "1h")) // reminder time was 11:30, after the last run

        scheduler.reschedule()
        assertEquals(Instant.parse("2026-12-01T11:30:00Z"), alarm.at) // in the past: fires immediately
        scheduler.fireDue()

        assertEquals(listOf("soon"), shown.single().map { it.event.id })
    }

    @Test
    fun `a reminder time before the last run is never shown late`() = runTest {
        scheduler.reschedule() // watermark = now (12:00)
        eventDao.upsert(event("soon", "2026-12-01T12:30:00Z", "1h")) // reminder time 11:30, before the watermark

        scheduler.reschedule()
        scheduler.fireDue()

        assertNull(alarm.at)
        assertTrue(shown.isEmpty())
    }

    @Test
    fun `a clock set back clamps the watermark so later reminders still fire`() = runTest {
        scheduler.reschedule() // watermark 12:00
        now = Instant.parse("2026-12-01T08:00:00Z") // clock moved back
        eventDao.upsert(event("a", "2026-12-01T10:00:00Z", "at_time"))

        scheduler.reschedule()
        assertEquals(Instant.parse("2026-12-01T10:00:00Z"), alarm.at)
    }

    @Test
    fun `the time zone is read on every run`() = runTest {
        eventDao.upsert(event("day", "2026-12-10T00:00:00Z", "at_time", allDay = true))
        scheduler.reschedule()
        assertEquals(Instant.parse("2026-12-10T09:00:00Z"), alarm.at) // 09:00 in Lisbon

        zone = ZoneId.of("America/New_York")
        scheduler.reschedule()
        assertEquals(Instant.parse("2026-12-10T14:00:00Z"), alarm.at) // 09:00 in New York
    }
}
