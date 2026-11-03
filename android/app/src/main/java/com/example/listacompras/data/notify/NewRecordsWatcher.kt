package com.example.listacompras.data.notify

import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.local.entity.SyncMetaEntity
import com.example.listacompras.data.repository.CurrentUserProvider
import com.example.listacompras.domain.repository.NewItemsTracker
import java.util.concurrent.atomic.AtomicBoolean

/** A synced record the other user may have added: its server `created` time, its author and what to show. */
data class NewRecordCandidate(val createdAt: Long, val author: String, val label: String)

/**
 * Decides when to notify about records added by the other user; shared by the shopping list and the
 * calendar. Two markers in `sync_meta` ([keySeen], [keyNotified]), both server `created` times:
 * - seen: what the user has had on screen. Records of the other user newer than this are "unseen"
 *   and all of them are listed in the notification.
 * - notified: what was already announced, so a dismissed notification never comes back.
 *
 * Server times only: [load] returns synced records, never pending ones (they carry the local clock).
 * With no markers yet (first run) both start at the newest existing record, so nothing old is
 * announced. [firstRunFloor], when given, raises that starting point (for example to "now"): needed
 * when the pull of a collection can fail on its own, which would otherwise leave the markers at 0
 * and announce the whole history once it arrives.
 */
class NewRecordsWatcher(
    private val syncMeta: SyncMetaDao,
    private val keySeen: String,
    private val keyNotified: String,
    private val currentUser: CurrentUserProvider,
    private val enabled: suspend () -> Boolean,
    private val load: suspend () -> List<NewRecordCandidate>?,
    private val show: (List<String>) -> Unit,
    private val cancel: () -> Unit,
    private val firstRunFloor: (() -> Long)? = null,
) : NewItemsTracker {

    private val visible = AtomicBoolean(false)

    /** Called by the periodic worker after a sync. */
    suspend fun check() {
        if (visible.get()) return markSeen() // being looked at right now: nothing to announce
        val me = currentUser.currentUserId() ?: return
        val records = load() ?: return
        val newest = records.maxOfOrNull { it.createdAt } ?: 0L
        val seen = marker(keySeen)
        val notified = marker(keyNotified)
        if (seen == null || notified == null) {
            val start = maxOf(newest, floor())
            put(keySeen, start); put(keyNotified, start)
            return
        }
        if (!enabled()) {
            // Off: stay in step, so turning it on later does not announce old records.
            put(keySeen, maxOf(seen, newest)); put(keyNotified, maxOf(notified, newest))
            return
        }
        val unseen = records.filter { it.author != me && it.createdAt > seen }
        if (unseen.any { it.createdAt > notified }) {
            show(unseen.map { it.label })
            put(keyNotified, maxOf(notified, unseen.maxOf { it.createdAt }))
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
        cancel()
        val newest = load()?.maxOfOrNull { it.createdAt } ?: return
        put(keySeen, maxOf(marker(keySeen) ?: floor(), newest))
        put(keyNotified, maxOf(marker(keyNotified) ?: floor(), newest))
    }

    private fun floor() = firstRunFloor?.invoke() ?: 0L

    private suspend fun marker(key: String) = syncMeta.get(key)?.toLongOrNull()

    private suspend fun put(key: String, value: Long) = syncMeta.put(SyncMetaEntity(key, value.toString()))
}
