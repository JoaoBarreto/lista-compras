package com.example.listacompras.data.sync

import com.example.listacompras.domain.model.ListKind
import com.example.listacompras.data.local.entity.OpType
import com.example.listacompras.data.remote.PocketBaseApiFactory
import com.example.listacompras.data.remote.TokenProvider
import com.example.listacompras.data.repository.CountingSyncScheduler
import com.example.listacompras.data.repository.CurrentUserProvider
import com.example.listacompras.data.repository.FakeActiveList
import com.example.listacompras.data.repository.FakeItemDao
import com.example.listacompras.data.repository.FakeEventDao
import com.example.listacompras.data.repository.FakeEventTypeDao
import com.example.listacompras.data.repository.FakePendingOpDao
import com.example.listacompras.data.repository.FakeSyncMetaDao
import com.example.listacompras.data.repository.ItemRepositoryImpl
import com.example.listacompras.data.repository.TransactionRunner
import com.example.listacompras.data.sync.FakePocketBaseApi.Failure
import com.example.listacompras.domain.model.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock

class OutboxProcessorTest {

    private val json = PocketBaseApiFactory.json
    private val itemDao = FakeItemDao()
    private val opDao = FakePendingOpDao()
    private val metaDao = FakeSyncMetaDao()
    private val api = FakePocketBaseApi()
    private val eventTypeDao = FakeEventTypeDao()
    private val eventDao = FakeEventDao()
    private val logs = mutableListOf<String>()
    private var token: String? = "token"
    private var seq = 0
    private val tx = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    private val repo = ItemRepositoryImpl(
        itemDao, opDao, tx, FakeActiveList("list1"),
        object : CurrentUserProvider { override fun currentUserId() = "user1" },
        json, Clock.systemUTC(), CountingSyncScheduler(), newId = { "item%011d".format(++seq) },
    )
    private val processor = OutboxProcessor(
        opDao, itemDao, eventDao, eventTypeDao, metaDao, tx, OutboxCoalescer(opDao, tx, json), api,
        TokenProvider { token }, json, log = { logs += it }, lock = SyncLock(),
    )

    private suspend fun addItem(name: String): String {
        repo.add(ListKind.SHOPPING, name)
        return itemDao.items.value.keys.last()
    }

    @Test
    fun `without a session nothing is sent, discarded or changed`() = runTest {
        addItem("Leite")
        token = null

        assertEquals(SyncOutcome.AuthLost, processor.process())

        assertEquals(0, api.calls)
        assertEquals(1, opDao.getAll().size)
        assertEquals(0, opDao.getAll().single().attempts)
        assertEquals(null, metaDao.get(OutboxProcessor.DISCARDED_KEY))
    }

    @Test
    fun `an empty outbox is done without calls`() = runTest {
        assertEquals(SyncOutcome.Done, processor.process())
        assertEquals(0, api.calls)
    }

    @Test
    fun `sends the outbox in order and marks items synced`() = runTest {
        val id = addItem("Leite")
        repo.setChecked(id, true)

        assertEquals(SyncOutcome.Done, processor.process())

        assertTrue(opDao.ops.isEmpty())
        // CREATE and UPDATE were coalesced into one request.
        assertEquals(1, api.calls)
        assertEquals(true, api.server.getValue(id).checked)
        assertEquals(SyncState.SYNCED, itemDao.items.value.getValue(id).syncState)
    }

    @Test
    fun `failing then recovering empties the outbox without duplicating items`() = runTest {
        val a = addItem("A")
        val b = addItem("B")
        api.failures += Failure.NetworkDown
        assertEquals(SyncOutcome.Retry, processor.process())
        assertEquals(2, opDao.ops.size)
        assertTrue(api.server.isEmpty())

        api.failures += Failure.Http(503)
        assertEquals(SyncOutcome.Retry, processor.process())

        assertEquals(SyncOutcome.Done, processor.process())
        assertTrue(opDao.ops.isEmpty())
        assertEquals(listOf(a, b), api.server.keys.toList())
        assertEquals(setOf(SyncState.SYNCED), itemDao.items.value.values.map { it.syncState }.toSet())
    }

    @Test
    fun `a CREATE whose response was lost is not duplicated and ends synced`() = runTest {
        val id = addItem("Leite")
        api.failures += Failure.ResponseLost

        assertEquals(SyncOutcome.Retry, processor.process())
        assertEquals(1, api.server.size)
        assertEquals(1, opDao.ops.single().attempts)

        // The retry gets 400 (duplicate id); the probe finds the record, so it counts as sent.
        assertEquals(SyncOutcome.Done, processor.process())

        assertTrue(opDao.ops.isEmpty())
        assertEquals(1, api.server.size)
        assertEquals(SyncState.SYNCED, itemDao.items.value.getValue(id).syncState)
        assertTrue(logs.isEmpty())
    }

    @Test
    fun `an attempted CREATE followed by a DELETE still deletes on the server`() = runTest {
        val id = addItem("Leite")
        api.failures += Failure.ResponseLost
        processor.process()
        repo.delete(id)

        assertEquals(SyncOutcome.Done, processor.process())

        assertTrue(api.server.isEmpty())
        assertTrue(opDao.ops.isEmpty())
    }

    @Test
    fun `a 400 on a first CREATE is discarded, logged and counted`() = runTest {
        addItem("Leite")
        api.failures += Failure.Http(400)

        assertEquals(SyncOutcome.Done, processor.process())

        assertTrue(opDao.ops.isEmpty())
        assertEquals(1, logs.size)
        assertTrue("Leite" !in logs.single())
        assertEquals("1", metaDao.values.value[OutboxProcessor.DISCARDED_KEY])
    }

    @Test
    fun `a 400 on an attempted CREATE that is not on the server is discarded`() = runTest {
        addItem("Leite")
        api.failures += Failure.NetworkDown
        processor.process()
        api.failures += Failure.Http(400)

        assertEquals(SyncOutcome.Done, processor.process())

        assertTrue(opDao.ops.isEmpty())
        assertEquals("1", metaDao.values.value[OutboxProcessor.DISCARDED_KEY])
    }

    @Test
    fun `a 404 on UPDATE is discarded and a 404 on DELETE counts as sent`() = runTest {
        val id = addItem("A")
        processor.process()
        api.server.clear()
        repo.setChecked(id, true)
        assertEquals(SyncOutcome.Done, processor.process())
        assertEquals("1", metaDao.values.value[OutboxProcessor.DISCARDED_KEY])

        repo.delete(id)
        assertEquals(SyncOutcome.Done, processor.process())
        assertEquals("1", metaDao.values.value[OutboxProcessor.DISCARDED_KEY])
        assertTrue(opDao.ops.isEmpty())
    }

    @Test
    fun `a 403 is discarded so the queue is not blocked`() = runTest {
        addItem("A")
        addItem("B")
        api.failures += Failure.Http(403)

        assertEquals(SyncOutcome.Done, processor.process())

        assertEquals(1, api.server.size)
        assertTrue(opDao.ops.isEmpty())
    }

    @Test
    fun `401 retries while a token exists and reports AuthLost once the session is gone`() = runTest {
        addItem("A")
        api.failures += Failure.Http(401)
        assertEquals(SyncOutcome.Retry, processor.process())

        token = null
        api.failures += Failure.Http(401)
        assertEquals(SyncOutcome.AuthLost, processor.process())
        assertEquals(1, opDao.ops.size)
    }

    @Test
    fun `5xx stops the pass and keeps later operations in order`() = runTest {
        addItem("A")
        addItem("B")
        api.failures += Failure.Http(500)

        assertEquals(SyncOutcome.Retry, processor.process())

        assertEquals(2, opDao.ops.size)
        assertEquals(1, api.calls)
        assertEquals(listOf(1, 0), opDao.ops.map { it.attempts })
    }

    @Test
    fun `a malformed payload is discarded`() = runTest {
        opDao.insert(
            com.example.listacompras.data.local.entity.PendingOpEntity(
                itemId = "x", type = OpType.CREATE, payloadJson = "not json", createdAt = 0,
            ),
        )

        assertEquals(SyncOutcome.Done, processor.process())

        assertTrue(opDao.ops.isEmpty())
        assertEquals("1", metaDao.values.value[OutboxProcessor.DISCARDED_KEY])
    }
}
