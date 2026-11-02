package com.example.listacompras.data.notify

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

/** Posts one notification with a fixed id, so a newer list replaces the older one. */
class AndroidNewItemsNotifier(private val context: Context) : NewItemsNotifier {

    // The permission is checked by canPost() on the first line.
    @SuppressLint("MissingPermission")
    override fun show(names: List<String>) {
        if (names.isEmpty() || !canPost()) return
        createChannel()
        val count = names.size
        val shown = names.take(MAX_NAMES).joinToString(", ")
        val text = if (count > MAX_NAMES) {
            context.getString(R.string.notif_shopping_more, shown, count - MAX_NAMES)
        } else {
            shown
        }
        val title = context.resources.getQuantityString(R.plurals.notif_shopping_title, count, count)
        // The lock screen gets a generic version: no item names.
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .build()
        val tap = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    override fun cancel() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun canPost() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_shopping),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "shopping_new_items"
        const val NOTIFICATION_ID = 1001
        const val MAX_NAMES = 5
    }
}
