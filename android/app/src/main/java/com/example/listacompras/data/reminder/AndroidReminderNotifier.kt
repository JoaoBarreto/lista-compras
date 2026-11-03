package com.example.listacompras.data.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.listacompras.MainActivity
import com.example.listacompras.R
import com.example.listacompras.domain.reminder.DueReminder
import com.example.listacompras.domain.reminder.ReminderNotifier
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** One notification per event, with the event id as its key so a repeated reminder replaces the older one. */
class AndroidReminderNotifier(
    private val context: Context,
    private val zone: () -> ZoneId,
) : ReminderNotifier {

    // The permission is checked by canPost() on the first line.
    @SuppressLint("MissingPermission")
    override fun show(reminders: List<DueReminder>) {
        if (reminders.isEmpty() || !canPost()) return
        createChannel()
        val tap = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // The lock screen gets a generic version: no event title.
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.notif_event_public))
            .build()
        reminders.forEach { reminder ->
            val text = describe(reminder)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(reminder.event.title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setAutoCancel(true)
                .setContentIntent(tap)
                .build()
            NotificationManagerCompat.from(context).notify(reminder.event.id, NOTIFICATION_TAG_ID, notification)
        }
    }

    private fun describe(reminder: DueReminder): String {
        val event = reminder.event
        val type = reminder.typeName ?: context.getString(R.string.calendar_unknown_type)
        val locale = Locale.getDefault()
        val date = event.startsAt.atZone(if (event.allDay) ZoneOffset.UTC else zone()).toLocalDate()
        val day = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date)
        return if (event.allDay) {
            context.getString(R.string.notif_event_text_all_day, day, type)
        } else {
            val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
                .format(event.startsAt.atZone(zone()).toLocalTime())
            context.getString(R.string.notif_event_text_timed, day, time, type)
        }
    }

    private fun canPost() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_events),
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "event_reminders"
        const val NOTIFICATION_TAG_ID = 2002
    }
}
