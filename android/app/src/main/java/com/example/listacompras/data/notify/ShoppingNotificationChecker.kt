package com.example.listacompras.data.notify

import com.example.listacompras.data.local.dao.ItemDao
import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.local.entity.SyncMetaEntity
import com.example.listacompras.data.repository.ActiveListProvider
import com.example.listacompras.data.repository.CurrentUserProvider
import com.example.listacompras.domain.model.ListKind
import com.example.listacompras.domain.model.SyncState
import com.example.listacompras.domain.repository.NewItemsTracker
import com.example.listacompras.domain.repository.NotificationSettings
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decides when to notify about shopping items added by the other user.
 *
 * Two markers in `sync_meta`, both server `created` times of the newest synced item seen:
 * - `lastSeenItemAt`: what the user has had on screen. Items of the other user newer than this are
 *   "unseen" and all of them are listed in the notification.
 * - `lastNotifiedItemAt`: what was already notified, so a dismissed notification never comes back.
 *
 * Server times only: a pending item of this phone carries the local clock and is never used.
 * With no markers yet (first run) both start at the newest existing item, so nothing old is notified.
 */
class ShoppingNotificationChecker(
    private val itemDao: ItemDao,
    private val syncMeta: SyncMetaDao,
    private val activeList: ActiveListProvider,
    private val currentUser: CurrentUserProvider,
    private val settings: NotificationSettings,
    private val notifier: NewItemsNotifier,
) : NewItemsTracker {

    private val visible = AtomicBoolean(false)

    /** Called by the periodic worker after a sync. */
    suspend fun check() {
        if (visible.get()) return markSeen() // being looked at right now: nothing to announce
        val me = currentUser.currentUserId() ?: return
        val items = syncedItems() ?: return
        val newest = items.maxOfOrNull { it.createdAt } ?: 0L
        val seen = marker(KEY_SEEN)
        val notified = marker(KEY_NOTIFIED)
        if (seen == null || notified == null) {
            put(KEY_SEEN, newest); put(KEY_NOTIFIED, newest)
            return
        }
        if (!settings.shoppingEnabled.first()) {
            // Off: stay in step, so turning it on later does not announce old items.
            put(KEY_SEEN, maxOf(seen, newest)); put(KEY_NOTIFIED, maxOf(notified, newest))
            return
        }
        val unseen = items.filter { it.addedBy != me && it.createdAt > seen }
        if (unseen.any { it.createdAt > notified }) {
            notifier.show(unseen.map { it.name })
            put(KEY_NOTIFIED, maxOf(notified, unseen.maxOf { it.createdAt }))
        }
    }

    override suspend fun onAppVisible() {
        visible.set(true)
        markSeen()
    }

    override suspend fun onAppHidden() {
        markSeen()
        visible.set(false)
    }

    private suspend fun markSeen() {
        notifier.cancel()
        val newest = syncedItems()?.maxOfOrNull { it.createdAt } ?: return
        put(KEY_SEEN, maxOf(marker(KEY_SEEN) ?: 0L, newest))
        put(KEY_NOTIFIED, maxOf(marker(KEY_NOTIFIED) ?: 0L, newest))
    }

    private suspend fun syncedItems() =
        activeList.listId(ListKind.SHOPPING)?.takeIf { it.isNotEmpty() }
            ?.let { itemDao.getItems(it) }
            ?.filter { it.syncState == SyncState.SYNCED }

    private suspend fun marker(key: String) = syncMeta.get(key)?.toLongOrNull()

    private suspend fun put(key: String, value: Long) = syncMeta.put(SyncMetaEntity(key, value.toString()))

    private companion object {
        const val KEY_SEEN = "lastSeenItemAt"
        const val KEY_NOTIFIED = "lastNotifiedItemAt"
    }
}
