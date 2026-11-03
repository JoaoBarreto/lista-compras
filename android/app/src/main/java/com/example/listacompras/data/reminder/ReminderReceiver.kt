package com.example.listacompras.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.listacompras.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Handles the reminder alarm and the system events that clear or invalidate alarms: reboot, app
 * update, clock and time-zone changes. All of them do the same: show what is due, then set the
 * next alarm from the database.
 */
class ReminderReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun scheduler(): ReminderScheduler

        @ApplicationScope
        fun scope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val dependencies = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        val pending = goAsync()
        dependencies.scope().launch {
            try {
                dependencies.scheduler().fireDue()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.joaobarreto.listacompras.EVENT_REMINDER"

        private val HANDLED_ACTIONS = setOf(
            ACTION_REMINDER,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
