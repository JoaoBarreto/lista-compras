package com.example.listacompras.data.local

import com.example.listacompras.data.local.entity.OpType
import com.example.listacompras.data.local.entity.PendingOpEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingOpDaoTest : DaoTestBase() {

    private val dao get() = db.pendingOpDao()

    private fun op(itemId: String, type: OpType = OpType.UPDATE, createdAt: Long = 0) =
        PendingOpEntity(itemId = itemId, type = type, payloadJson = "{}", createdAt = createdAt)

    @Test
    fun opsComeBackInInsertionOrder() = runTest {
        val first = dao.insert(op("a", OpType.CREATE))
        val second = dao.insert(op("b"))
        val third = dao.insert(op("a"))

        assertTrue(first < second && second < third)
        assertEquals(listOf(first, second, third), dao.getAll().map { it.opId })
    }

    @Test
    fun getForItemReturnsOnlyThatItemsOps() = runTest {
        dao.insert(op("a", OpType.CREATE))
        dao.insert(op("b"))
        dao.insert(op("a"))

        assertEquals(listOf(OpType.CREATE, OpType.UPDATE), dao.getForItem("items", "a").map { it.type })
    }

    @Test
    fun incrementAttemptsAndDelete() = runTest {
        val id = dao.insert(op("a"))

        dao.incrementAttempts(id)
        dao.incrementAttempts(id)
        assertEquals(2, dao.getAll().single().attempts)

        dao.delete(id)
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun updatePayloadChangesOnlyThatRow() = runTest {
        val a = dao.insert(op("a"))
        dao.insert(op("b"))

        dao.updatePayload(a, "{\"checked\":true}")

        assertEquals(listOf("{\"checked\":true}", "{}"), dao.getAll().map { it.payloadJson })
    }

    @Test
    fun deleteForItemKeepsOtherItems() = runTest {
        dao.insert(op("a"))
        dao.insert(op("b"))

        dao.deleteForItem("items", "a")

        assertEquals(listOf("b"), dao.getAll().map { it.itemId })
    }

    @Test
    fun opSurvivesDeletingItsItemRow() = runTest {
        db.itemDao().upsert(item("a"))
        dao.insert(op("a", OpType.DELETE))

        db.itemDao().deleteById("a")

        assertEquals(listOf(OpType.DELETE), dao.getForItem("items", "a").map { it.type })
    }

    @Test
    fun observeCountReEmitsOnInsertAndDelete() = runTest {
        val emissions = Channel<Int>(Channel.UNLIMITED)
        backgroundScope.launch(Dispatchers.Default) {
            dao.observeCount().collect { emissions.send(it) }
        }
        assertEquals(0, emissions.receive())

        val id = dao.insert(op("a"))
        assertEquals(1, emissions.receive())

        dao.delete(id)
        assertEquals(0, emissions.receive())
    }

    @Test
    fun pendingItemIdsAreDistinct() = runTest {
        dao.insert(op("a"))
        dao.insert(op("a"))
        dao.insert(op("b"))

        assertEquals(setOf("a", "b"), dao.getPendingItemIds("items").toSet())
        assertEquals(2, dao.getPendingItemIds("items").size)
    }
}
