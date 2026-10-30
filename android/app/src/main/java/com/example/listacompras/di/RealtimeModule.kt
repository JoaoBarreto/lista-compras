package com.example.listacompras.di

import android.util.Log
import com.example.listacompras.BuildConfig
import com.example.listacompras.data.auth.TokenStore
import com.example.listacompras.data.local.dao.ItemDao
import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.remote.PocketBaseApi
import com.example.listacompras.data.remote.PocketBaseApiFactory
import com.example.listacompras.data.repository.ActiveListProvider
import com.example.listacompras.data.repository.TransactionRunner
import com.example.listacompras.data.sync.RealtimeClient
import com.example.listacompras.data.sync.RealtimeEventApplier
import com.example.listacompras.data.sync.Reconciler
import com.example.listacompras.data.sync.SyncLock
import com.example.listacompras.domain.repository.RealtimeSync
import com.example.listacompras.domain.repository.SyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RealtimeModule {

    private const val TAG = "Realtime"

    @Provides
    @Singleton
    fun provideRealtimeEventApplier(
        itemDao: ItemDao,
        pendingOpDao: PendingOpDao,
        activeList: ActiveListProvider,
        transactions: TransactionRunner,
        lock: SyncLock,
    ): RealtimeEventApplier = RealtimeEventApplier(
        itemDao, pendingOpDao, activeList, transactions, lock, PocketBaseApiFactory.json,
        // Only the action is logged, never record contents.
        log = { Log.d(TAG, it) },
    )

    @Provides
    @Singleton
    fun provideRealtimeSync(
        client: OkHttpClient,
        api: PocketBaseApi,
        tokenStore: TokenStore,
        applier: RealtimeEventApplier,
        reconciler: Reconciler,
        scheduler: SyncScheduler,
    ): RealtimeSync = RealtimeClient(
        // A stream never ends on its own, so it must not have a read timeout.
        sseClient = client.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build(),
        baseUrl = BuildConfig.SERVER_URL,
        api = api,
        tokens = tokenStore.tokenFlow,
        applier = applier,
        pull = reconciler,
        scheduler = scheduler,
        json = PocketBaseApiFactory.json,
        log = { Log.d(TAG, it) },
    )
}
