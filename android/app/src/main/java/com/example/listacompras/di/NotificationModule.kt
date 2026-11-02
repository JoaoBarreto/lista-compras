package com.example.listacompras.di

import android.content.Context
import com.example.listacompras.data.local.dao.ItemDao
import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.notify.AndroidNewItemsNotifier
import com.example.listacompras.data.notify.NewItemsNotifier
import com.example.listacompras.data.notify.ShoppingNotificationChecker
import com.example.listacompras.data.notify.SyncMetaNotificationSettings
import com.example.listacompras.data.repository.ActiveListProvider
import com.example.listacompras.data.repository.CurrentUserProvider
import com.example.listacompras.domain.repository.NewItemsTracker
import com.example.listacompras.domain.repository.NotificationSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {

    @Provides
    @Singleton
    fun provideNotificationSettings(dao: SyncMetaDao): NotificationSettings = SyncMetaNotificationSettings(dao)

    @Provides
    @Singleton
    fun provideNewItemsNotifier(@ApplicationContext context: Context): NewItemsNotifier =
        AndroidNewItemsNotifier(context)

    // One instance: the worker and the activity share the "app is visible" flag.
    @Provides
    @Singleton
    fun provideShoppingNotificationChecker(
        itemDao: ItemDao,
        syncMeta: SyncMetaDao,
        activeList: ActiveListProvider,
        currentUser: CurrentUserProvider,
        settings: NotificationSettings,
        notifier: NewItemsNotifier,
    ): ShoppingNotificationChecker =
        ShoppingNotificationChecker(itemDao, syncMeta, activeList, currentUser, settings, notifier)

    @Provides
    fun provideNewItemsTracker(checker: ShoppingNotificationChecker): NewItemsTracker = checker
}
