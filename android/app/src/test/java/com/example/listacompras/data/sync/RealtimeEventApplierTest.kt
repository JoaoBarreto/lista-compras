package com.example.listacompras.data.sync

import com.example.listacompras.domain.model.ListKind
import com.example.listacompras.data.local.entity.ItemEntity
import com.example.listacompras.data.remote.PocketBaseApiFactory
import com.example.listacompras.data.repository.CountingSyncScheduler
import com.example.listacompras.data.repository.CurrentUserProvider
import com.example.listacompras.data.repository.FakeActiveList
import com.example.listacompras.data.repository.FakeItemDao
import com.example.listacompras.data.repository.FakeEventDao
import com.example.listacompras.data.repository.FakeEventTypeDao
import com.example.listacompras.data.repository.FakePendingOpDao
import com.example.listacompras.data.repository.ItemRepositoryImpl
import com.example.listacompras.data.repository.TransactionRunner
import com.example.listacompras.domain.model.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock

class RealtimeEventApplierTest {

    private val json = PocketBaseApiFactory.json
    private val itemDao = FakeItemDao()
    private val opDao = FakePendingOpDao()
    private val eventTypeDao = FakeEventTypeDao()
    private val eventDao = FakeEventDao()
    private val logs = mutableListOf<String>()
    private val tx = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }
    private val activeList = FakeActiveList("list1")
    private val applier = RealtimeEventApplier(itemDao, eventTypeDao, eventDao, opDao, activeList, tx, SyncLock(), json) { logs += it }

    private fun record(id: String, name: String = id, list: String = "list1", checked: Boolean = false) =
        """{"id":"$id","list":"$list","name":"$name","quantity":"","checked":$checked,"category":"",""" +
            """"added_by":"u","checked_by":"","created":"2026-10-29 12:00:00.000Z","updated":"2026-10-29 12:00:01.000Z"}"""

    private fun event(action: String, record: String) = """{"action":"$action","record":$record}"""

    private fun local(id: String, name: String = id) =
        ItemEntity(id, "list1", name, null, false, null, "u", null, 1, 1, SyncState.SYNCED)

    @Test
    fun `create and update upsert the record as synced`() = runTest {
        applier.apply(event("create", record("a", name = "Leite")))
        assertEquals("Leite", itemDao.items.value.getValue("a").name)

        applier.apply(event("update", record("a", name = "Leite magro", checked = true)))

        val item = itemDao.items.value.getValue("a")
        assertEquals("Leite magro", item.name)
        assertTrue(item.checked)
        assertNull(item.quantity)
        assertNull(item.checkedBy)
        assertEquals(SyncState.SYNCED, item.syncState)
    }

    @Test
    fun `delete removes the item`() = runTest {
        itemDao.upsert(local("a"))

        applier.apply(event("delete", record("a")))

        assertTrue(itemDao.items.value.isEmpty())
    }

    @Test
    fun `an item with pending operations is left alone, including the echo of our own change`() = runTest {
        val repo = ItemRepositoryImpl(
            itemDao, opDao, tx, activeList,
            object : CurrentUserProvider { override fun currentUserId() = "u" },
            json, Clock.systemUTC(), CountingSyncScheduler(), newId = { "mine00000000001" },
        )
        repo.add(ListKind.SHOPPING, "Leite")

        applier.apply(event("create", record("mine00000000001", name = "server copy")))
        applier.apply(event("delete", record("mine00000000001")))

        assertEquals("Leite", itemDao.items.value.getValue("mine00000000001").name)
    }

    @Test
    fun `records of the tasks list are applied once it is known`() = runTest {
        activeList.tasksId.value = "list2"

        applier.apply(event("create", record("x", list = "list2")))

        assertEquals(setOf("x"), itemDao.items.value.keys)
    }

    @Test
    fun `records of another list are ignored`() = runTest {
        applier.apply(event("create", record("x", list = "list2")))

        assertTrue(itemDao.items.value.isEmpty())
    }

    @Test
    fun `malformed json and unknown actions are ignored without throwing`() = runTest {
        applier.apply("not json")
        applier.apply("""{"action":"create"}""")
        applier.apply(event("explode", record("a")))

        assertTrue(itemDao.items.value.isEmpty())
        assertTrue(logs.none { "Leite" in it })
    }

    @Test
    fun `an event with an unparseable date is ignored without throwing`() = runTest {
        val bad = record("a").replace("2026-10-29 12:00:00.000Z", "garbage")

        applier.apply(event("create", bad))

        assertTrue(itemDao.items.value.isEmpty())
    }
}
