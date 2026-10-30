package com.example.listacompras.di

import com.example.listacompras.data.auth.TokenStore
import com.example.listacompras.data.local.AppDatabase
import com.example.listacompras.data.local.dao.ItemDao
import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.remote.PocketBaseApi
import com.example.listacompras.data.remote.PocketBaseApiFactory
import com.example.listacompras.data.repository.ActiveListProvider
import com.example.listacompras.data.repository.CurrentUserProvider
import com.example.listacompras.data.repository.ItemRepositoryImpl
import com.example.listacompras.data.repository.ListRepositoryImpl
import com.example.listacompras.data.repository.RoomTransactionRunner
import com.example.listacompras.data.repository.SyncMetaActiveListProvider
import com.example.listacompras.data.repository.TransactionRunner
import com.example.listacompras.data.sync.OutboxCoalescer
import com.example.listacompras.data.sync.OutboxProcessor
import com.example.listacompras.domain.repository.SyncScheduler
import com.example.listacompras.data.sync.WorkManagerSyncScheduler
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.listacompras.domain.repository.ItemRepository
import com.example.listacompras.domain.repository.ListRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import java.time.Clock
import javax.inject.Singleton
import kotlin.random.Random

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    fun provideTransactionRunner(db: AppDatabase): TransactionRunner = RoomTransactionRunner(db)

    @Provides
    fun provideActiveListProvider(dao: SyncMetaDao): ActiveListProvider = SyncMetaActiveListProvider(dao)

    @Provides
    fun provideCurrentUserProvider(tokenStore: TokenStore): CurrentUserProvider = tokenStore

    @Provides
    @Singleton
    fun provideListRepository(
        api: PocketBaseApi,
        activeList: ActiveListProvider,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): ListRepository = ListRepositoryImpl(api, activeList, dispatcher)

    @Provides
    @Singleton
    fun provideSyncScheduler(@ApplicationContext context: Context): SyncScheduler =
        WorkManagerSyncScheduler(context)

    @Provides
    @Singleton
    fun provideOutboxProcessor(
        pendingOpDao: PendingOpDao,
        itemDao: ItemDao,
        syncMetaDao: SyncMetaDao,
        transactions: TransactionRunner,
        api: PocketBaseApi,
        tokenStore: TokenStore,
    ): OutboxProcessor = OutboxProcessor(
        pendingOpDao = pendingOpDao,
        itemDao = itemDao,
        syncMetaDao = syncMetaDao,
        transactions = transactions,
        coalescer = OutboxCoalescer(pendingOpDao, transactions, PocketBaseApiFactory.json),
        api = api,
        tokenProvider = tokenStore,
        json = PocketBaseApiFactory.json,
        // Only the operation type and HTTP code are logged: never names or tokens.
        log = { Log.w("OutboxProcessor", it) },
    )

    @Provides
    @Singleton
    fun provideItemRepository(
        itemDao: ItemDao,
        pendingOpDao: PendingOpDao,
        transactions: TransactionRunner,
        activeList: ActiveListProvider,
        currentUser: CurrentUserProvider,
        syncScheduler: SyncScheduler,
    ): ItemRepository = ItemRepositoryImpl(
        itemDao = itemDao,
        pendingOpDao = pendingOpDao,
        transactions = transactions,
        activeList = activeList,
        currentUser = currentUser,
        json = PocketBaseApiFactory.json,
        clock = Clock.systemUTC(),
        syncScheduler = syncScheduler,
        newId = ::newRecordId,
    )
}

private const val ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"

/** PocketBase record id: 15 characters `[a-z0-9]`. */
internal fun newRecordId(): String = buildString { repeat(15) { append(ID_CHARS[Random.nextInt(ID_CHARS.length)]) } }
