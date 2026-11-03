package com.example.listacompras.domain.reminder

import com.example.listacompras.domain.model.CalendarEvent
import com.example.listacompras.domain.model.Reminder
import com.example.listacompras.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReminderTimesTest {

    private val lisbon = ZoneId.of("Europe/Lisbon")

    private fun event(
        id: String,
        startsAt: String,
        reminder: Reminder?,
        allDay: Boolean = false,
    ) = CalendarEvent(id, id, null, Instant.parse(startsAt), allDay, "t", reminder, "u", Instant.EPOCH, SyncState.SYNCED)

    @Test
    fun `a timed event fires relative to its start`() {
        val start = "2026-12-10T20:00:00Z"
        assertEquals(Instant.parse(start), ReminderTimes.fireAt(event("a", start, Reminder.AT_TIME), lisbon))
        assertEquals(Instant.parse("2026-12-10T19:00:00Z"), ReminderTimes.fireAt(event("a", start, Reminder.ONE_HOUR), lisbon))
        assertEquals(Instant.parse("2026-12-09T20:00:00Z"), ReminderTimes.fireAt(event("a", start, Reminder.ONE_DAY), lisbon))
        assertEquals(Instant.parse("2026-12-05T20:00:00Z"), ReminderTimes.fireAt(event("a", start, Reminder.FIVE_DAYS), lisbon))
        assertNull(ReminderTimes.fireAt(event("a", start, null), lisbon))
    }

    @Test
    fun `days before keep the wall clock across a DST change`() {
        // Lisbon moves to summer time on 2027-03-28. 20:00 local on the 29th, 2 days before = 20:00 local on the 27th.
        val e = event("a", "2027-03-29T19:00:00Z", Reminder.TWO_DAYS) // 20:00 local (UTC+1)
        assertEquals(Instant.parse("2027-03-27T20:00:00Z"), ReminderTimes.fireAt(e, lisbon)) // 20:00 local (UTC+0)
    }

    @Test
    fun `an all-day event is anchored to 09h local on its date`() {
        val day = "2026-12-10T00:00:00Z"
        assertEquals(Instant.parse("2026-12-10T09:00:00Z"), ReminderTimes.fireAt(event("a", day, Reminder.AT_TIME, true), lisbon))
        assertEquals(Instant.parse("2026-12-10T08:00:00Z"), ReminderTimes.fireAt(event("a", day, Reminder.ONE_HOUR, true), lisbon))
        assertEquals(Instant.parse("2026-12-09T09:00:00Z"), ReminderTimes.fireAt(event("a", day, Reminder.ONE_DAY, true), lisbon))
        // Same date in a negative-offset zone: 09:00 there.
        assertEquals(
            Instant.parse("2026-12-10T14:00:00Z"),
            ReminderTimes.fireAt(event("a", day, Reminder.AT_TIME, true), ZoneId.of("America/New_York")),
        )
    }

    @Test
    fun `next is the earliest reminder after the watermark`() {
        val now = Instant.parse("2026-12-01T12:00:00Z")
        val events = listOf(
            event("past", "2026-11-30T12:00:00Z", Reminder.AT_TIME),
            event("later", "2026-12-20T12:00:00Z", Reminder.AT_TIME),
            event("soon", "2026-12-05T12:00:00Z", Reminder.ONE_HOUR),
            event("none", "2026-12-02T12:00:00Z", null),
        )
        assertEquals(Instant.parse("2026-12-05T11:00:00Z"), ReminderTimes.next(events, now, lisbon))
        assertNull(ReminderTimes.next(emptyList(), now, lisbon))
    }

    @Test
    fun `due returns reminders fired in the window for events not expired, oldest first`() {
        val after = Instant.parse("2026-12-01T10:00:00Z")
        val now = Instant.parse("2026-12-01T12:00:00Z")
        val events = listOf(
            event("started", "2026-12-01T11:00:00Z", Reminder.AT_TIME), // fired 11:00, started 1 h ago: still within the grace
            event("a", "2026-12-02T12:00:00Z", Reminder.ONE_DAY),       // fired 12:00 = now
            event("b", "2026-12-01T13:00:00Z", Reminder.ONE_HOUR),      // fired 12:00 = now
            event("old", "2026-12-01T09:30:00Z", Reminder.AT_TIME),     // before the window
            event("future", "2026-12-03T12:00:00Z", Reminder.AT_TIME),
            event("gone", "2026-12-01T10:30:00Z", Reminder.AT_TIME),    // fired 10:30, but over (started 1.5 h ago, ok) -> due
        )
        assertEquals(listOf("gone", "started", "a", "b"), ReminderTimes.due(events, after, now, lisbon).map { it.id })
    }

    @Test
    fun `an at-time reminder is due when delivered exactly at the start or a few minutes late`() {
        val e = event("a", "2026-12-10T20:00:00Z", Reminder.AT_TIME)
        val after = Instant.parse("2026-12-10T19:00:00Z")
        assertEquals(listOf("a"), ReminderTimes.due(listOf(e), after, Instant.parse("2026-12-10T20:00:00Z"), lisbon).map { it.id })
        assertEquals(listOf("a"), ReminderTimes.due(listOf(e), after, Instant.parse("2026-12-10T20:09:00Z"), lisbon).map { it.id })
    }

    @Test
    fun `a reminder is dropped once the grace after the start has passed`() {
        val e = event("a", "2026-12-10T20:00:00Z", Reminder.AT_TIME)
        val after = Instant.parse("2026-12-10T19:00:00Z")
        assertEquals(emptyList<String>(), ReminderTimes.due(listOf(e), after, Instant.parse("2026-12-10T22:30:00Z"), lisbon).map { it.id })
    }

    @Test
    fun `an all-day reminder stays valid until the end of its day`() {
        val e = event("a", "2026-12-10T00:00:00Z", Reminder.AT_TIME, allDay = true) // fires 09:00
        val after = Instant.parse("2026-12-10T06:00:00Z")
        assertEquals(listOf("a"), ReminderTimes.due(listOf(e), after, Instant.parse("2026-12-10T20:00:00Z"), lisbon).map { it.id })
        assertEquals(emptyList<String>(), ReminderTimes.due(listOf(e), after, Instant.parse("2026-12-11T01:00:00Z"), lisbon).map { it.id })
    }
}
