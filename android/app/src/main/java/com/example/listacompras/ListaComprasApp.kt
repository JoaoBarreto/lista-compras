package com.example.listacompras

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.listacompras.data.notify.NotificationCheckScheduler
import com.example.listacompras.data.reminder.ReminderScheduler
import com.example.listacompras.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ListaComprasApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        NotificationCheckScheduler.schedule(this)
        // Any change to the events (local edit, pull, realtime) re-sets the reminder alarm.
        reminderScheduler.observe(applicationScope)
    }

    // The default WorkManagerInitializer is removed in the manifest so this factory is used.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
