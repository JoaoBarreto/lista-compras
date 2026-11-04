package com.example.listacompras.data.repository

import com.example.listacompras.domain.model.ListKind
import com.example.listacompras.data.local.dao.ItemDao
import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.local.entity.ItemEntity
import com.example.listacompras.data.local.entity.OpType
import com.example.listacompras.data.local.entity.PendingOpEntity
import com.example.listacompras.data.remote.PocketBaseApiFactory
import com.example.listacompras.domain.model.Category
import com.example.listacompras.domain.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ItemRepositoryImplTest {

    private val itemDao = FakeItemDao()
    private val opDao = FakePendingOpDao()
    private val activeList = FakeActiveList("list1")
    private var userId: String? = "user1"
    private var ids = generateSequence(1) { it + 1 }.map { "id%012d".format(it) }.iterator()
    private val scheduler = CountingSyncScheduler()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)

    private val repo = ItemRepositoryImpl(
        itemDao = itemDao,
        pendingOpDao = opDao,
        transactions = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        },
        activeList = activeList,
        currentUser = object : CurrentUserProvider { override fun currentUserId() = userId },
        json = PocketBaseApiFactory.json,
        clock = clock,
        syncScheduler = scheduler,
        newId = { ids.next() },
    )

    private suspend fun addMilk() = repo.add(ListKind.SHOPPING, "Leite", "2", Category.DAIRY).also { assertTrue(it) }

    @Test
    fun `add stores a pending item and a CREATE op with the full body`() = runTest {
        addMilk()

        val item = itemDao.items.value.values.single()
        assertEquals("id000000000001", item.id)
        assertEquals("Leite", item.name)
        assertEquals("dairy", item.category)
        assertEquals("user1", item.addedBy)
        assertEquals(SyncState.PENDING, item.syncState)
        val op = opDao.ops.single()
        assertEquals(OpType.CREATE, op.type)
        assertEquals(item.id, op.itemId)
        assertEquals(
            """{"id":"id000000000001","list":"list1","name":"Leite","quantity":"2","checked":false,""" +
                """"category":"dairy","added_by":"user1"}""",
            op.payloadJson,
        )
    }

    @Test
    fun `add omits blank quantity and missing category from the body`() = runTest {
        repo.add(ListKind.SHOPPING, "Pão", "  ", null)

        val op = opDao.ops.single()
        assertEquals(
            """{"id":"id000000000001","list":"list1","name":"Pão","checked":false,"added_by":"user1"}""",
            op.payloadJson,
        )
        assertNull(itemDao.items.value.values.single().quantity)
    }

    @Test
    fun `add without active list or user changes nothing`() = runTest {
        activeList.id.value = null
        assertFalse(repo.add(ListKind.SHOPPING, "Leite"))
        activeList.id.value = "list1"
        userId = null
        assertFalse(repo.add(ListKind.SHOPPING, "Leite"))

        assertTrue(itemDao.items.value.isEmpty())
        assertTrue(opDao.ops.isEmpty())
    }

    @Test
    fun `update renames, changes the quantity and queues a PATCH with both`() = runTest {
        addMilk()
        val id = itemDao.items.value.keys.single()
        val before = scheduler.requests

        repo.update(id, "  Leite magro ", " 6 ")

        val item = itemDao.items.value.getValue(id)
        assertEquals("Leite magro", item.name)
        assertEquals("6", item.quantity)
        assertEquals(SyncState.PENDING, item.syncState)
        val op = opDao.ops.last()
        assertEquals(OpType.UPDATE, op.type)
        assertEquals("""{"name":"Leite magro","quantity":"6"}""", op.payloadJson)
        assertEquals(before + 1, scheduler.requests)
    }

    @Test
    fun `update with a blank quantity clears it and sends an explicit empty string`() = runTest {
        addMilk()
        val id = itemDao.items.value.keys.single()

        repo.update(id, "Leite", "  ")

        assertNull(itemDao.items.value.getValue(id).quantity)
        assertEquals("""{"name":"Leite","quantity":""}""", opDao.ops.last().payloadJson)
    }

    @Test
    fun `update with a null quantity leaves it alone and sends only the name`() = runTest {
        addMilk()
        val id = itemDao.items.value.keys.single()

        repo.update(id, "Leite magro", null)

        assertEquals("2", itemDao.items.value.getValue(id).quantity)
        assertEquals("""{"name":"Leite magro"}""", opDao.ops.last().payloadJson)
    }

    @Test
    fun `update that changes nothing, has a blank name or an unknown id does nothing`() = runTest {
        addMilk()
        val id = itemDao.items.value.keys.single()
        val ops = opDao.ops.size
        val before = scheduler.requests

        repo.update(id, " Leite ", "2")
        repo.update(id, "Leite", null)
        repo.update(id, "   ", "2")
        repo.update("missing", "Leite", "2")

        assertEquals(ops, opDao.ops.size)
        assertEquals(before, scheduler.requests)
    }

    @Test
    fun `setChecked true updates the item and queues an UPDATE with checked_by`() = runTest {
        addMilk()
        val id = itemDao.items.value.keys.single()

        repo.setChecked(id, true)

        val item = itemDao.items.value.getValue(id)
        assertTrue(item.checked)
        assertEquals("user1", item.checkedBy)
        val op = opDao.ops.last()
        assertEquals(OpType.UPDATE, op.type)
        assertEquals("""{"checked":true,"checked_by":"user1"}""", op.payloadJson)
    }

    @Test
    fun `setChecked false sends an explicit empty checked_by to clear the relation`() = runTest {
        addMilk()
        val id = itemDao.items.value.keys.single()
        repo.setChecked(id, true)

        repo.setChecked(id, false)

        val item = itemDao.items.value.getValue(id)
        assertFalse(item.checked)
        assertNull(item.checkedBy)
        assertEquals("""{"checked":false,"checked_by":""}""", opDao.ops.last().payloadJson)
    }

    @Test
    fun `setChecked on an unknown id does nothing`() = runTest {
        repo.setChecked("missing", true)

        assertTrue(opDao.ops.isEmpty())
    }

    @Test
    fun `delete removes the item and queues a DELETE op`() = runTest {
        addMilk()
        val id = itemDao.items.value.keys.single()

        repo.delete(id)

        assertTrue(itemDao.items.value.isEmpty())
        val op = opDao.ops.last()
        assertEquals(OpType.DELETE, op.type)
        assertEquals(id, op.itemId)
        assertEquals("{}", op.payloadJson)
    }

    @Test
    fun `clearChecked deletes only checked items and queues one DELETE each`() = runTest {
        repo.add(ListKind.SHOPPING, "A"); repo.add(ListKind.SHOPPING, "B"); repo.add(ListKind.SHOPPING, "C")
        val (a, b, c) = itemDao.items.value.keys.toList()
        repo.setChecked(a, true)
        repo.setChecked(c, true)
        opDao.ops.clear()

        repo.clearChecked(ListKind.SHOPPING)

        assertEquals(setOf(b), itemDao.items.value.keys)
        assertEquals(listOf(OpType.DELETE, OpType.DELETE), opDao.ops.map { it.type })
        assertEquals(setOf(a, c), opDao.ops.map { it.itemId }.toSet())
    }

    @Test
    fun `each effective write requests a sync and no-ops or failed adds do not`() = runTest {
        activeList.id.value = null
        repo.add(ListKind.SHOPPING, "x")
        activeList.id.value = "list1"
        assertEquals(0, scheduler.requests)

        addMilk()
        val id = itemDao.items.value.keys.single()
        repo.setChecked(id, true)
        repo.delete(id)
        assertEquals(3, scheduler.requests)

        repo.setChecked("missing", true)
        repo.delete("missing")
        repo.clearChecked(ListKind.SHOPPING)
        assertEquals(3, scheduler.requests)

        repo.add(ListKind.SHOPPING, "A")
        repo.setChecked(itemDao.items.value.keys.single(), true)
        repo.clearChecked(ListKind.SHOPPING)
        assertEquals(6, scheduler.requests)
    }

    @Test
    fun `observeItems maps entities and turns an unknown category into null`() = runTest {
        itemDao.upsert(entity("x1", category = "dairy"))
        itemDao.upsert(entity("x2", category = "from_the_future", createdAt = 2))
        itemDao.upsert(entity("x3", category = "", createdAt = 3))

        val items = repo.observeItems(ListKind.SHOPPING).first()

        assertEquals(listOf(Category.DAIRY, null, null), items.map { it.category })
        assertEquals(Instant.ofEpochMilli(1_000), items.first().updatedAt)
    }

    @Test
    fun `observeItems is empty while there is no active list`() = runTest {
        activeList.id.value = null
        itemDao.upsert(entity("x1", category = null))

        assertTrue(repo.observeItems(ListKind.SHOPPING).first().isEmpty())
    }

    private fun entity(id: String, category: String?, createdAt: Long = 1) = ItemEntity(
        id = id, listId = "list1", name = id, quantity = null, checked = false, category = category,
        addedBy = "user1", checkedBy = null, createdAt = createdAt, updatedAt = 1_000,
        syncState = SyncState.SYNCED,
    )

    @Test
    fun `tasks and shopping items are kept apart`() = runTest {
        activeList.tasksId.value = "tasks1"
        repo.add(ListKind.SHOPPING, "Leite")
        repo.add(ListKind.TASKS, "Limpar grelhador")

        assertEquals(listOf("Leite"), repo.observeItems(ListKind.SHOPPING).first().map { it.name })
        assertEquals(listOf("Limpar grelhador"), repo.observeItems(ListKind.TASKS).first().map { it.name })
    }

    @Test
    fun `clearing the checked tasks leaves shopping alone`() = runTest {
        activeList.tasksId.value = "tasks1"
        repo.add(ListKind.SHOPPING, "Leite")
        repo.add(ListKind.TASKS, "Limpar grelhador")
        itemDao.items.value.keys.forEach { repo.setChecked(it, true) }

        repo.clearChecked(ListKind.TASKS)

        assertEquals(listOf("Leite"), itemDao.items.value.values.map { it.name })
    }

    @Test
    fun `adding a task is refused while the tasks list is unknown`() = runTest {
        assertFalse(repo.add(ListKind.TASKS, "Limpar grelhador"))
    }
}
