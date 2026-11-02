package com.example.listacompras.data.sync

import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.local.entity.SyncMetaEntity
import com.example.listacompras.domain.model.SyncStatus
import com.example.listacompras.domain.repository.SyncStatusProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Precedence, highest first:
 * 1. [SyncStatus.Error]: the server rejected operations that the user has not acknowledged yet.
 *    (Losing the session is not an error here: it sends the user to the login screen, and a 401
 *    with a surviving token is a network problem, so it shows as [SyncStatus.Offline].)
 * 2. [SyncStatus.Offline]: no network, or the server is unreachable.
 * 3. [SyncStatus.Syncing]: operations waiting to be sent, or reachability not known yet.
 * 4. [SyncStatus.Synced].
 */
internal fun deriveSyncStatus(
    online: Boolean,
    reachability: Reachability,
    pendingOps: Int,
    hasUnacknowledgedDiscards: Boolean,
): SyncStatus = when {
    hasUnacknowledgedDiscards -> SyncStatus.Error
    !online || reachability == Reachability.Unreachable -> SyncStatus.Offline
    pendingOps > 0 || reachability == Reachability.Unknown -> SyncStatus.Syncing
    else -> SyncStatus.Synced
}

class SyncStatusMonitor(
    private val pendingOpDao: PendingOpDao,
    private val syncMetaDao: SyncMetaDao,
    network: NetworkMonitor,
    reachability: ServerReachability,
) : SyncStatusProvider {

    override val status: Flow<SyncStatus> = combine(
        network.online,
        reachability.state,
        pendingOpDao.observeCount(),
        syncMetaDao.observe(OutboxProcessor.DISCARDED_KEY),
        syncMetaDao.observe(ACK_KEY),
    ) { online, reach, pending, discarded, acknowledged ->
        val hasDiscards = discarded.toCount() > acknowledged.toCount()
        deriveSyncStatus(online, reach, pending, hasDiscards)
    }.distinctUntilChanged()

    override suspend fun acknowledgeErrors() {
        val discarded = syncMetaDao.get(OutboxProcessor.DISCARDED_KEY) ?: return
        syncMetaDao.put(SyncMetaEntity(ACK_KEY, discarded))
    }

    private fun String?.toCount(): Long = this?.toLongOrNull() ?: 0L

    companion object {
        /** Value of `discardedOps` the user has already seen. */
        const val ACK_KEY = "discardedAck"
    }
}
