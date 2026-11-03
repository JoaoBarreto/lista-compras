package com.example.listacompras.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.listacompras.domain.reminder.ReminderAlarms
import java.time.Instant

/**
 * One RTC alarm that fires [ReminderReceiver]. Exact when the system allows it (it always does with
 * USE_EXACT_ALARM on Android 13+), otherwise approximate but still delivered in Doze.
 */
class AndroidReminderAlarms(private val context: Context) : ReminderAlarms {

    private val manager = context.getSystemService(AlarmManager::class.java)

    override fun scheduleAt(at: Instant) {
        val millis = at.toEpochMilli()
        val intent = pendingIntent()
        // Before Android 12 exact alarms are always allowed.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    override fun cancel() = manager.cancel(pendingIntent())

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMINDER),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val REQUEST_CODE = 2001
    }
}
