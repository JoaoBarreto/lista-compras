package com.example.listacompras.domain.reminder

import java.time.Instant

/** The single system alarm that wakes the app at the next reminder. Faked in unit tests. */
interface ReminderAlarms {

    /** Replaces any previous alarm. A time in the past fires immediately. */
    fun scheduleAt(at: Instant)

    fun cancel()
}
