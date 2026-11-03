package com.example.listacompras.data.notify

import com.example.listacompras.data.local.dao.EventDao
import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.repository.CurrentUserProvider
import com.example.listacompras.domain.model.SyncState
import com.example.listacompras.domain.repository.EventNotificationSettings
import com.example.listacompras.domain.repository.NewItemsTracker
import kotlinx.coroutines.flow.first

/**
 * Notifies about calendar events created by the other user, with the same rules as the shopping
 * list ([NewRecordsWatcher]). Markers: `lastSeenEventAt` and `lastNotifiedEventAt`.
 *
 * The calendar pull is isolated from the items pull and can fail on its own, so the first markers
 * start at "now" at least: history that arrives later is never announced.
 */
class EventNotificationChecker(
    eventDao: EventDao,
    syncMeta: SyncMetaDao,
    currentUser: CurrentUserProvider,
    settings: EventNotificationSettings,
    notifier: NewItemsNotifier,
    now: () -> Long,
) : NewItemsTracker {

    private val watcher = NewRecordsWatcher(
        syncMeta = syncMeta,
        keySeen = "lastSeenEventAt",
        keyNotified = "lastNotifiedEventAt",
        currentUser = currentUser,
        enabled = { settings.eventsEnabled.first() },
        load = {
            eventDao.getAll()
                .filter { it.syncState == SyncState.SYNCED }
                .sortedBy { it.createdAt }
                .map { NewRecordCandidate(it.createdAt, it.createdBy, it.title) }
        },
        show = notifier::show,
        cancel = notifier::cancel,
        firstRunFloor = now,
    )

    suspend fun check() = watcher.check()

    override suspend fun onAppVisible() = watcher.onAppVisible()

    override suspend fun onAppHidden() = watcher.onAppHidden()
}
