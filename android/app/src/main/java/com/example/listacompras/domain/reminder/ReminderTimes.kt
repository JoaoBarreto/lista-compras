package com.example.listacompras.domain.reminder

import com.example.listacompras.domain.model.CalendarEvent
import com.example.listacompras.domain.model.Reminder
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * When the reminder of an event fires. Pure functions of the event, the time and the zone.
 *
 * A timed event fires relative to its start. An all-day event is stored at 00:00 UTC, which is
 * not a useful moment to wake someone, so it is anchored to [ALL_DAY_HOUR] local time on its date.
 */
object ReminderTimes {

    /** Local hour at which "at the event" fires for an all-day event; "1 h before" is one hour earlier. */
    val ALL_DAY_HOUR: LocalTime = LocalTime.of(9, 0)

    /** How long after the start of a timed event its reminders may still be shown. */
    val GRACE: Duration = Duration.ofHours(2)

    /** The moment the reminder fires, or null when the event has none. */
    fun fireAt(event: CalendarEvent, zone: ZoneId): Instant? {
        val reminder = event.reminder ?: return null
        val start = anchor(event, zone)
        val local = start.atZone(zone)
        return when (reminder) {
            Reminder.AT_TIME -> start
            Reminder.ONE_HOUR -> start.minus(Duration.ofHours(1))
            Reminder.ONE_DAY -> local.minusDays(1).toInstant()
            Reminder.TWO_DAYS -> local.minusDays(2).toInstant()
            Reminder.THREE_DAYS -> local.minusDays(3).toInstant()
            Reminder.FOUR_DAYS -> local.minusDays(4).toInstant()
            Reminder.FIVE_DAYS -> local.minusDays(5).toInstant()
        }
    }

    /**
     * After this moment a reminder is no longer shown. A timed event keeps [GRACE] after its start,
     * because an exact alarm can arrive late (Doze, or the inexact fallback) and "at the event" fires
     * exactly at the start. An all-day event lasts until the end of its date.
     */
    fun expiresAt(event: CalendarEvent, zone: ZoneId): Instant =
        if (event.allDay) {
            event.startsAt.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        } else {
            event.startsAt.plus(GRACE)
        }

    /**
     * The earliest reminder that fires after [watermark], or null. Measured from the watermark (the last
     * moment reminders were shown up to) and not from "now", so a reminder that is due but was not
     * delivered yet is never skipped: a time in the past simply fires immediately.
     */
    fun next(events: List<CalendarEvent>, watermark: Instant, zone: ZoneId): Instant? =
        events.mapNotNull { fireAt(it, zone) }.filter { it.isAfter(watermark) }.minOrNull()

    /**
     * Events whose reminder fired in (after, now] and that are not expired, so a reminder missed
     * while the phone was off is still shown, but never for an event that already passed.
     */
    fun due(events: List<CalendarEvent>, after: Instant, now: Instant, zone: ZoneId): List<CalendarEvent> =
        events.filter { event ->
            val at = fireAt(event, zone) ?: return@filter false
            at.isAfter(after) && !at.isAfter(now) && now.isBefore(expiresAt(event, zone))
        }.sortedWith(compareBy({ fireAt(it, zone) }, { it.id }))

    private fun anchor(event: CalendarEvent, zone: ZoneId): Instant =
        if (event.allDay) {
            event.startsAt.atZone(ZoneOffset.UTC).toLocalDate().atTime(ALL_DAY_HOUR).atZone(zone).toInstant()
        } else {
            event.startsAt
        }
}
