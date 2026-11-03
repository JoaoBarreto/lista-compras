package com.example.listacompras.domain.reminder

import com.example.listacompras.domain.model.CalendarEvent

/** A reminder to show: the event and the name of its type (null if the type is not known locally). */
data class DueReminder(val event: CalendarEvent, val typeName: String?)

interface ReminderNotifier {

    fun show(reminders: List<DueReminder>)
}
