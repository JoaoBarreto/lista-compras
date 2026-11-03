package com.example.listacompras.data.reminder

import com.example.listacompras.data.local.dao.EventDao
import com.example.listacompras.data.local.dao.EventTypeDao
import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.local.entity.SyncMetaEntity
import com.example.listacompras.data.repository.toDomain
import com.example.listacompras.domain.reminder.DueReminder
import com.example.listacompras.domain.reminder.ReminderAlarms
import com.example.listacompras.domain.reminder.ReminderNotifier
import com.example.listacompras.domain.reminder.ReminderTimes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

/**
 * Keeps one system alarm set to the next event reminder, computed from the local database.
 *
 * Only [fireDue] shows notifications and advances the watermark (the moment up to which reminders
 * were shown); [reschedule] only sets the alarm, measured from the watermark, so a reminder that is
 * due but not delivered yet is never lost. Everything that changes events (local edits, pull,
 * realtime) goes through Room, so observing it covers them all.
 *
 * [zone] is read on every run: the process survives a time-zone change.
 */
class ReminderScheduler(
    private val eventDao: EventDao,
    private val eventTypeDao: EventTypeDao,
    private val syncMetaDao: SyncMetaDao,
    private val alarms: ReminderAlarms,
    private val notifier: ReminderNotifier,
    private val now: () -> Instant,
    private val zone: () -> ZoneId,
) {

    private val lock = Mutex()

    /** Reschedules after every change of the events, for as long as [scope] lives. */
    fun observe(scope: CoroutineScope) {
        scope.launch { eventDao.observeAll().collectLatest { reschedule() } }
    }

    suspend fun reschedule() = lock.withLock { scheduleNext(watermark()) }

    /** Shows the reminders that fired since the watermark, advances it and schedules the next one. */
    suspend fun fireDue() = lock.withLock {
        val current = now()
        val after = watermark()
        val events = eventDao.getAll().map { it.toDomain() }
        val due = ReminderTimes.due(events, after, current, zone())
        // Advance first: if showing fails, a reminder is lost rather than repeated on every retry.
        saveWatermark(current)
        if (due.isNotEmpty()) {
            val names = eventTypeDao.getAll().associate { it.id to it.name }
            notifier.show(due.map { DueReminder(it, names[it.typeId]) })
        }
        scheduleNext(current)
    }

    private suspend fun scheduleNext(after: Instant) {
        val next = ReminderTimes.next(eventDao.getAll().map { it.toDomain() }, after, zone())
        if (next == null) alarms.cancel() else alarms.scheduleAt(next)
    }

    /** First run: start at "now", so installing never replays old reminders. A clock set back clamps to "now". */
    private suspend fun watermark(): Instant {
        val current = now()
        val stored = syncMetaDao.get(WATERMARK_KEY)?.toLongOrNull()?.let(Instant::ofEpochMilli)
        if (stored == null || stored.isAfter(current)) {
            saveWatermark(current)
            return current
        }
        return stored
    }

    private suspend fun saveWatermark(at: Instant) {
        syncMetaDao.put(SyncMetaEntity(WATERMARK_KEY, at.toEpochMilli().toString()))
    }

    private companion object {
        const val WATERMARK_KEY = "reminderWatermark"
    }
}
