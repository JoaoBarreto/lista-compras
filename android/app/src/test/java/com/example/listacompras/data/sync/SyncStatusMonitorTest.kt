package com.example.listacompras.data.sync

import com.example.listacompras.data.local.entity.OpType
import com.example.listacompras.data.local.entity.PendingOpEntity
import com.example.listacompras.data.local.entity.SyncMetaEntity
import com.example.listacompras.data.repository.FakePendingOpDao
import com.example.listacompras.data.repository.FakeSyncMetaDao
import com.example.listacompras.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStatusMonitorTest {

    @Test
    fun `derivation covers every combination with the documented precedence`() {
        val reachabilities = Reachability.entries
        for (online in listOf(true, false)) for (reach in reachabilities) for (pending in listOf(0, 3)) {
            for (discards in listOf(true, false)) {
                val expected = when {
                    discards -> SyncStatus.Error
                    !online || reach == Reachability.Unreachable -> SyncStatus.Offline
                    pending > 0 || reach == Reachability.Unknown -> SyncStatus.Syncing
                    else -> SyncStatus.Synced
                }
                assertEquals(
                    "online=$online reach=$reach pending=$pending discards=$discards",
                    expected,
                    deriveSyncStatus(online, reach, pending, discards),
                )
            }
        }
    }

    @Test
    fun `representative cases`() {
        assertEquals(SyncStatus.Synced, deriveSyncStatus(true, Reachability.Reachable, 0, false))
        assertEquals(SyncStatus.Syncing, deriveSyncStatus(true, Reachability.Reachable, 2, false))
        // Airplane mode: offline wins even with pending operations and a stale Reachable.
        assertEquals(SyncStatus.Offline, deriveSyncStatus(false, Reachability.Reachable, 2, false))
        assertEquals(SyncStatus.Offline, deriveSyncStatus(true, Reachability.Unreachable, 0, false))
        // An unacknowledged discard outranks everything, even being offline.
        assertEquals(SyncStatus.Error, deriveSyncStatus(false, Reachability.Unreachable, 2, true))
    }

    private class FakeNetwork : NetworkMonitor {
        val flow = MutableStateFlow(true)
        override val online: Flow<Boolean> = flow
    }

    private val ops = FakePendingOpDao()
    private val meta = FakeSyncMetaDao()
    private val network = FakeNetwork()
    private val reachability = ServerReachability()
    private val monitor = SyncStatusMonitor(ops, meta, network, reachability)

    private suspend fun current() = monitor.status.first()

    @Test
    fun `follows the live inputs`() = runTest {
        reachability.markReachable()
        assertEquals(SyncStatus.Synced, current())

        val id = ops.insert(PendingOpEntity(itemId = "a", type = OpType.CREATE, payloadJson = "{}", createdAt = 0))
        assertEquals(SyncStatus.Syncing, current())

        ops.delete(id)
        assertEquals(SyncStatus.Synced, current())

        network.flow.value = false
        assertEquals(SyncStatus.Offline, current())

        network.flow.value = true
        reachability.markUnknown()
        assertEquals(SyncStatus.Syncing, current())
    }

    @Test
    fun `discarded operations show an error until acknowledged, and a new discard shows it again`() = runTest {
        reachability.markReachable()
        meta.put(SyncMetaEntity(OutboxProcessor.DISCARDED_KEY, "1"))
        assertEquals(SyncStatus.Error, current())

        monitor.acknowledgeErrors()
        assertEquals(SyncStatus.Synced, current())

        meta.put(SyncMetaEntity(OutboxProcessor.DISCARDED_KEY, "2"))
        assertEquals(SyncStatus.Error, current())
    }

    @Test
    fun `acknowledging with nothing discarded changes nothing`() = runTest {
        reachability.markReachable()

        monitor.acknowledgeErrors()

        assertEquals(SyncStatus.Synced, current())
    }
}
