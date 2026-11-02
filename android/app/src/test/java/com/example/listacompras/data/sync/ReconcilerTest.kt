package com.example.listacompras.data.sync

import com.example.listacompras.data.local.entity.ItemEntity
import com.example.listacompras.data.remote.PocketBaseApiFactory
import com.example.listacompras.data.remote.TokenProvider
import com.example.listacompras.data.remote.dto.ItemDto
import com.example.listacompras.data.repository.CountingSyncScheduler
import com.example.listacompras.data.repository.CurrentUserProvider
import com.example.listacompras.data.repository.FakeActiveList
import com.example.listacompras.data.repository.FakeItemDao
import com.example.listacompras.data.repository.FakePendingOpDao
import com.example.listacompras.data.repository.FakeSyncMetaDao
import com.example.listacompras.data.repository.ItemRepositoryImpl
import com.example.listacompras.data.repository.TransactionRunner
import com.example.listacompras.data.sync.FakePocketBaseApi.Failure
import com.example.listacompras.domain.model.SyncState
import com.example.listacompras.domain.repository.ListRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ReconcilerTest {

    private val json = PocketBaseApiFactory.json
    private val itemDao = FakeItemDao()
    private val opDao = FakePendingOpDao()
    private val metaDao = FakeSyncMetaDao()
    private val api = FakePocketBaseApi()
    private val activeList = FakeActiveList("list1")
    private var token: String? = "token"
    private var listAvailable = true
    private val clock = Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC)
    private val tx = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }
    private val lists = object : ListRepository {
        override suspend fun ensureActiveList(): Boolean {
            if (listAvailable) activeList.id.value = "list1"
            return listAvailable
        }
    }
    private val repo = ItemRepositoryImpl(
        itemDao, opDao, tx, activeList,
        object : CurrentUserProvider { override fun currentUserId() = "user1" },
        json, clock, CountingSyncScheduler(), newId = { "new000000000001" },
    )
    private val reconciler = Reconciler(
        api, itemDao, opDao, metaDao, activeList, lists, tx, SyncLock(), TokenProvider { token }, clock,
        pageSize = 2,
    )

    private fun remote(id: String, name: String = id, list: String = "list1", checked: Boolean = false) = ItemDto(
        id = id, list = list, name = name, quantity = "", checked = checked, category = "", addedBy = "u",
        checkedBy = "", created = Instant.ofEpochMilli(1_000), updated = Instant.ofEpochMilli(2_000),
    )

    private fun local(id: String, name: String = id, list: String = "list1", state: SyncState = SyncState.SYNCED) =
        ItemEntity(id, list, name, null, false, null, "u", null, 1, 1, state)

    private suspend fun server(vararg items: ItemDto) = items.forEach { api.server[it.id] = it }

    @Test
    fun `remote replaces a stale synced local item and maps empty strings to null`() = runTest {
        itemDao.upsert(local("a", name = "old"))
        server(remote("a", name = "new", checked = true))

        assertEquals(SyncOutcome.Done, reconciler.pull())

        val item = itemDao.items.value.getValue("a")
        assertEquals("new", item.name)
        assertTrue(item.checked)
        assertNull(item.quantity)
        assertNull(item.category)
        assertNull(item.checkedBy)
        assertEquals(1_000, item.createdAt)
        assertEquals(2_000, item.updatedAt)
        assertEquals(SyncState.SYNCED, item.syncState)
        assertEquals("5000", metaDao.values.value[Reconciler.LAST_FULL_SYNC_KEY])
    }

    @Test
    fun `without a session the local items are untouched`() = runTest {
        itemDao.upsert(local("a"))
        token = null

        assertEquals(SyncOutcome.AuthLost, reconciler.pull())

        assertEquals(setOf("a"), itemDao.items.value.keys)
    }

    @Test
    fun `an item with a pending UPDATE keeps its local version`() = runTest {
        repo.add("Leite")
        val id = itemDao.items.value.keys.single()
        api.server[id] = remote(id, name = "Leite")
        repo.setChecked(id, true)
        server(remote(id, name = "from server"))

        reconciler.pull()

        val item = itemDao.items.value.getValue(id)
        assertEquals("Leite", item.name)
        assertTrue(item.checked)
        assertEquals(SyncState.PENDING, item.syncState)
    }

    @Test
    fun `a pending CREATE that is not on the server yet is kept`() = runTest {
        repo.add("Leite")
        server(remote("other"))

        reconciler.pull()

        assertEquals(setOf("new000000000001", "other"), itemDao.items.value.keys)
    }

    @Test
    fun `a pending DELETE is not resurrected by the pull`() = runTest {
        itemDao.upsert(local("a"))
        server(remote("a"))
        repo.delete("a")

        reconciler.pull()

        assertTrue("a" !in itemDao.items.value)
    }

    @Test
    fun `an item deleted remotely is deleted locally`() = runTest {
        itemDao.upsert(local("gone"))
        itemDao.upsert(local("kept"))
        server(remote("kept"))

        reconciler.pull()

        assertEquals(setOf("kept"), itemDao.items.value.keys)
    }

    @Test
    fun `items of another list are untouched`() = runTest {
        itemDao.upsert(local("x", list = "list2"))
        server(remote("a"), remote("y", list = "list2"))

        reconciler.pull()

        assertEquals(setOf("a", "x"), itemDao.items.value.keys)
    }

    @Test
    fun `all pages are fetched`() = runTest {
        api.maxPerPage = 2
        server(remote("a"), remote("b"), remote("c"), remote("d"), remote("e"))

        reconciler.pull()

        assertEquals(setOf("a", "b", "c", "d", "e"), itemDao.items.value.keys)
    }

    @Test
    fun `a failure on page 2 changes nothing`() = runTest {
        api.maxPerPage = 2
        itemDao.upsert(local("local-only"))
        server(remote("a"), remote("b"), remote("c"))
        api.onListItems = { page -> if (page == 1) api.failures += Failure.NetworkDown }

        assertEquals(SyncOutcome.Retry, reconciler.pull())

        assertEquals(setOf("local-only"), itemDao.items.value.keys)
        assertNull(metaDao.values.value[Reconciler.LAST_FULL_SYNC_KEY])
    }

    @Test
    fun `an empty server list deletes the synced local items`() = runTest {
        itemDao.upsert(local("a"))

        assertEquals(SyncOutcome.Done, reconciler.pull())

        assertTrue(itemDao.items.value.isEmpty())
    }

    @Test
    fun `a write made during the fetch keeps its local version`() = runTest {
        // Synced and with no pending ops when the pull starts, so only reading the pending ids
        // inside the apply transaction (after the fetch) protects it.
        itemDao.upsert(local("a", name = "Leite"))
        server(remote("a", name = "Leite"))
        assertTrue(opDao.ops.isEmpty())
        api.onListItems = { repo.setChecked("a", true) }

        reconciler.pull()

        val item = itemDao.items.value.getValue("a")
        assertTrue(item.checked)
        assertEquals(SyncState.PENDING, item.syncState)
    }

    @Test
    fun `without a known list it fetches it first and retries when that fails`() = runTest {
        activeList.id.value = null
        listAvailable = false
        assertEquals(SyncOutcome.Retry, reconciler.pull())
        assertEquals(0, api.calls)

        listAvailable = true
        server(remote("a"))
        assertEquals(SyncOutcome.Done, reconciler.pull())
        assertEquals(setOf("a"), itemDao.items.value.keys)
    }

    @Test
    fun `server errors retry and a 401 without a token is AuthLost`() = runTest {
        api.failures += Failure.Http(503)
        assertEquals(SyncOutcome.Retry, reconciler.pull())

        token = null
        api.failures += Failure.Http(401)
        assertEquals(SyncOutcome.AuthLost, reconciler.pull())
    }
}
