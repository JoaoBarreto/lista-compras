package com.example.listacompras.di

import android.content.Context
import com.example.listacompras.data.local.dao.EventDao
import com.example.listacompras.data.local.dao.EventTypeDao
import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.reminder.AndroidReminderAlarms
import com.example.listacompras.data.reminder.AndroidReminderNotifier
import com.example.listacompras.data.reminder.ReminderScheduler
import com.example.listacompras.domain.reminder.ReminderAlarms
import com.example.listacompras.domain.reminder.ReminderNotifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReminderModule {

    @Provides
    @Singleton
    fun provideReminderAlarms(@ApplicationContext context: Context): ReminderAlarms = AndroidReminderAlarms(context)

    @Provides
    @Singleton
    fun provideReminderNotifier(@ApplicationContext context: Context): ReminderNotifier =
        AndroidReminderNotifier(context, zone = { ZoneId.systemDefault() })

    // One instance: the receiver and the app-wide observer share its lock.
    @Provides
    @Singleton
    fun provideReminderScheduler(
        eventDao: EventDao,
        eventTypeDao: EventTypeDao,
        syncMetaDao: SyncMetaDao,
        alarms: ReminderAlarms,
        notifier: ReminderNotifier,
    ): ReminderScheduler = ReminderScheduler(
        eventDao, eventTypeDao, syncMetaDao, alarms, notifier,
        now = { Instant.now() },
        zone = { ZoneId.systemDefault() },
    )
}
