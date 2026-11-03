package com.example.listacompras.ui.calendar

import com.example.listacompras.domain.model.CalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Date rules of the calendar. A timed event is a date and a time in the phone's zone. An all-day
 * event is its date at 00:00 UTC and is read back in UTC, so it falls on the same day on any phone.
 */
object CalendarDates {

    fun toInstant(date: LocalDate, time: LocalTime, allDay: Boolean, zone: ZoneId): Instant =
        if (allDay) date.atStartOfDay(ZoneOffset.UTC).toInstant() else date.atTime(time).atZone(zone).toInstant()

    fun dateOf(event: CalendarEvent, zone: ZoneId): LocalDate =
        event.startsAt.atZone(if (event.allDay) ZoneOffset.UTC else zone).toLocalDate()

    fun timeOf(event: CalendarEvent, zone: ZoneId): LocalTime = event.startsAt.atZone(zone).toLocalTime().withSecond(0).withNano(0)

    /** The Material 3 date picker speaks UTC-midnight milliseconds. */
    fun toPickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    /** Events of [month], grouped by day, days in order; all-day events first, then by time. */
    fun groupByDay(events: List<CalendarEvent>, month: YearMonth, zone: ZoneId): List<DayGroup> =
        events.map { it to dateOf(it, zone) }
            .filter { YearMonth.from(it.second) == month }
            .groupBy({ it.second }, { it.first })
            .toSortedMap()
            .map { (date, dayEvents) ->
                DayGroup(date, dayEvents.sortedWith(compareByDescending<CalendarEvent> { it.allDay }.thenBy { it.startsAt }.thenBy { it.id }))
            }
}

data class DayGroup(val date: LocalDate, val events: List<CalendarEvent>)
