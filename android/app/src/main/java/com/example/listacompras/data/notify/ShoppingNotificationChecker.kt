package com.example.listacompras.data.notify

import com.example.listacompras.data.local.dao.ItemDao
import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.repository.ActiveListProvider
import com.example.listacompras.data.repository.CurrentUserProvider
import com.example.listacompras.domain.model.ListKind
import com.example.listacompras.domain.model.SyncState
import com.example.listacompras.domain.repository.NewItemsTracker
import com.example.listacompras.domain.repository.NotificationSettings
import kotlinx.coroutines.flow.first

/**
 * Notifies about shopping items added by the other user; the rules live in [NewRecordsWatcher].
 * Markers: `lastSeenItemAt` and `lastNotifiedItemAt` (server `created` time of the newest synced item).
 */
class ShoppingNotificationChecker(
    private val itemDao: ItemDao,
    syncMeta: SyncMetaDao,
    private val activeList: ActiveListProvider,
    currentUser: CurrentUserProvider,
    settings: NotificationSettings,
    notifier: NewItemsNotifier,
) : NewItemsTracker {

    private val watcher = NewRecordsWatcher(
        syncMeta = syncMeta,
        keySeen = "lastSeenItemAt",
        keyNotified = "lastNotifiedItemAt",
        currentUser = currentUser,
        enabled = { settings.shoppingEnabled.first() },
        load = {
            activeList.listId(ListKind.SHOPPING)?.takeIf { it.isNotEmpty() }
                ?.let { itemDao.getItems(it) }
                ?.filter { it.syncState == SyncState.SYNCED }
                ?.map { NewRecordCandidate(it.createdAt, it.addedBy, it.name) }
        },
        show = notifier::show,
        cancel = notifier::cancel,
    )

    /** Called by the periodic worker after a sync. */
    suspend fun check() = watcher.check()

    override suspend fun onAppVisible() = watcher.onAppVisible()

    override suspend fun onAppHidden() = watcher.onAppHidden()
}
