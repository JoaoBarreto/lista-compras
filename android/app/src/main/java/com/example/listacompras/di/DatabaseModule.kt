package com.example.listacompras.di

import android.content.Context
import androidx.room.Room
import com.example.listacompras.data.local.AppDatabase
import com.example.listacompras.data.local.MIGRATION_1_2
import com.example.listacompras.data.local.dao.EventDao
import com.example.listacompras.data.local.dao.EventTypeDao
import com.example.listacompras.data.local.dao.ItemDao
import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.local.dao.SyncMetaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // No destructive fallback: app updates must keep the user's data (needs real migrations).
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideItemDao(db: AppDatabase): ItemDao = db.itemDao()

    @Provides
    fun providePendingOpDao(db: AppDatabase): PendingOpDao = db.pendingOpDao()

    @Provides
    fun provideSyncMetaDao(db: AppDatabase): SyncMetaDao = db.syncMetaDao()

    @Provides
    fun provideEventTypeDao(db: AppDatabase): EventTypeDao = db.eventTypeDao()

    @Provides
    fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()
}
