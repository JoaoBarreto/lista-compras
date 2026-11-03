package com.example.listacompras.ui.calendar

import com.example.listacompras.domain.model.CalendarEvent
import com.example.listacompras.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class CalendarDatesTest {

    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val newYork = ZoneId.of("America/New_York")

    private fun event(id: String, startsAt: Instant, allDay: Boolean = false) = CalendarEvent(
        id, id, null, startsAt, allDay, "t", null, "u", Instant.EPOCH, SyncState.SYNCED,
    )

    @Test
    fun `a timed event is its date and time in the phone zone`() {
        val instant = CalendarDates.toInstant(LocalDate.of(2026, 12, 1), LocalTime.of(23, 30), false, newYork)
        assertEquals(Instant.parse("2026-12-02T04:30:00Z"), instant)
        val e = event("a", instant)
        assertEquals(LocalDate.of(2026, 12, 1), CalendarDates.dateOf(e, newYork))
        assertEquals(LocalTime.of(23, 30), CalendarDates.timeOf(e, newYork))
    }

    @Test
    fun `an all-day event is midnight UTC and stays on its day in any zone`() {
        val date = LocalDate.of(2026, 12, 1)
        val instant = CalendarDates.toInstant(date, LocalTime.of(15, 0), true, newYork)
        assertEquals(Instant.parse("2026-12-01T00:00:00Z"), instant)
        val e = event("a", instant, allDay = true)
        assertEquals(date, CalendarDates.dateOf(e, newYork))
        assertEquals(date, CalendarDates.dateOf(e, lisbon))
        assertEquals(date, CalendarDates.dateOf(e, ZoneId.of("Pacific/Auckland")))
    }

    @Test
    fun `a time keeps its wall clock across a DST change`() {
        // 2026-03-29 02:00 -> 03:00 in Lisbon: 09:00 is UTC+1 after the change, UTC+0 before it.
        val before = CalendarDates.toInstant(LocalDate.of(2026, 3, 28), LocalTime.of(9, 0), false, lisbon)
        val after = CalendarDates.toInstant(LocalDate.of(2026, 3, 30), LocalTime.of(9, 0), false, lisbon)
        assertEquals(Instant.parse("2026-03-28T09:00:00Z"), before)
        assertEquals(Instant.parse("2026-03-30T08:00:00Z"), after)
        assertEquals(LocalTime.of(9, 0), CalendarDates.timeOf(event("a", after), lisbon))
    }

    @Test
    fun `picker millis round-trip any date, also in a negative-offset zone`() {
        val date = LocalDate.of(2026, 12, 31)
        assertEquals(date, CalendarDates.fromPickerMillis(CalendarDates.toPickerMillis(date)))
        assertEquals(1_798_675_200_000L, CalendarDates.toPickerMillis(date))
    }

    @Test
    fun `switching a timed event to all-day and back keeps its date`() {
        val date = LocalDate.of(2026, 12, 1)
        val time = LocalTime.of(22, 0)
        val allDay = event("a", CalendarDates.toInstant(date, time, true, newYork), allDay = true)
        assertEquals(date, CalendarDates.dateOf(allDay, newYork))
        val timed = event("a", CalendarDates.toInstant(date, time, false, newYork))
        assertEquals(date, CalendarDates.dateOf(timed, newYork))
    }

    @Test
    fun `grouping keeps only the month, orders days and puts all-day first`() {
        val events = listOf(
            event("late", CalendarDates.toInstant(LocalDate.of(2026, 12, 2), LocalTime.of(20, 0), false, lisbon)),
            event("early", CalendarDates.toInstant(LocalDate.of(2026, 12, 2), LocalTime.of(8, 0), false, lisbon)),
            event("all", CalendarDates.toInstant(LocalDate.of(2026, 12, 2), LocalTime.NOON, true, lisbon), allDay = true),
            event("first", CalendarDates.toInstant(LocalDate.of(2026, 12, 1), LocalTime.NOON, false, lisbon)),
            event("other", CalendarDates.toInstant(LocalDate.of(2027, 1, 1), LocalTime.NOON, false, lisbon)),
        )

        val groups = CalendarDates.groupByDay(events, YearMonth.of(2026, 12), lisbon)

        assertEquals(listOf(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2)), groups.map { it.date })
        assertEquals(listOf("all", "early", "late"), groups[1].events.map { it.id })
    }

    @Test
    fun `an event just after midnight groups by the local day of the zone`() {
        // 23:30 on 31 Dec in New York is already 1 Jan in UTC.
        val e = event("a", CalendarDates.toInstant(LocalDate.of(2026, 12, 31), LocalTime.of(23, 30), false, newYork))
        assertEquals(1, CalendarDates.groupByDay(listOf(e), YearMonth.of(2026, 12), newYork).size)
        assertEquals(0, CalendarDates.groupByDay(listOf(e), YearMonth.of(2027, 1), newYork).size)
    }

    @Test
    fun `a month that starts on a Sunday has six leading blanks and six rows`() {
        val month = YearMonth.of(2026, 11) // 1 Nov 2026 is a Sunday
        assertEquals(java.time.DayOfWeek.SUNDAY, month.atDay(1).dayOfWeek)

        val cells = CalendarDates.monthCells(month)

        assertEquals(42, cells.size)
        assertEquals(6, cells.takeWhile { it == null }.size)
        assertEquals(LocalDate.of(2026, 11, 1), cells[6])
        assertEquals(LocalDate.of(2026, 11, 30), cells.last { it != null })
        assertEquals(0, cells.size % 7)
    }

    @Test
    fun `a 28-day February starting on a Monday is exactly four full rows`() {
        val month = YearMonth.of(2027, 2) // 1 Feb 2027 is a Monday
        assertEquals(java.time.DayOfWeek.MONDAY, month.atDay(1).dayOfWeek)
        assertEquals(28, month.lengthOfMonth())

        val cells = CalendarDates.monthCells(month)

        assertEquals(28, cells.size)
        assertEquals(LocalDate.of(2027, 2, 1), cells.first())
        assertEquals(LocalDate.of(2027, 2, 28), cells.last())
    }

    @Test
    fun `every cell sits in the column of its weekday and the header starts on Monday`() {
        val header = CalendarDates.weekdays()
        assertEquals(java.time.DayOfWeek.MONDAY, header.first())
        assertEquals(java.time.DayOfWeek.SUNDAY, header.last())
        listOf(YearMonth.of(2026, 11), YearMonth.of(2027, 2), YearMonth.of(2026, 12)).forEach { month ->
            CalendarDates.monthCells(month).forEachIndexed { index, date ->
                if (date != null) assertEquals(header[index % 7], date.dayOfWeek)
            }
        }
    }
}
