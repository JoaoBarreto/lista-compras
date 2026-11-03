package com.example.listacompras.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.listacompras.R
import com.example.listacompras.domain.model.Reminder
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** User-facing date and reminder text. Locale formats keep the PT-PT wording of the phone's settings. */
internal object CalendarFormats {
    fun fullDate(date: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()).format(date)

    fun shortDate(date: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).format(date)

    fun time(time: LocalTime): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).format(time)

    fun month(month: YearMonth): String =
        DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()).format(month)
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

@Composable
internal fun reminderLabel(reminder: Reminder?): String = stringResource(
    when (reminder) {
        null -> R.string.reminder_none
        Reminder.AT_TIME -> R.string.reminder_at_time
        Reminder.ONE_HOUR -> R.string.reminder_1h
        Reminder.ONE_DAY -> R.string.reminder_1d
        Reminder.TWO_DAYS -> R.string.reminder_2d
        Reminder.THREE_DAYS -> R.string.reminder_3d
        Reminder.FOUR_DAYS -> R.string.reminder_4d
        Reminder.FIVE_DAYS -> R.string.reminder_5d
    },
)
