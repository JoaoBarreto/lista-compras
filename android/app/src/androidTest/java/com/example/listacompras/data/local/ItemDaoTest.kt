package com.example.listacompras.data.local

import com.example.listacompras.data.local.entity.ItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemDaoTest : DaoTestBase() {

    private val dao get() = db.itemDao()

    @Test
    fun observeReturnsItemsOrderedByCreatedAtForOneList() = runTest {
        dao.upsertAll(
            listOf(
                item("b", createdAt = 20),
                item("a", createdAt = 10),
                item("x", listId = "other", createdAt = 5),
            ),
        )

        assertEquals(listOf("a", "b"), dao.observeItems("list1").first().map { it.id })
    }

    @Test
    fun upsertReplacesExistingRow() = runTest {
        dao.upsert(item("a"))
        dao.upsert(item("a").copy(name = "Leite", checked = true))

        val items = dao.getItems("list1")
        assertEquals(1, items.size)
        assertEquals("Leite", items[0].name)
        assertEquals(true, items[0].checked)
    }

    @Test
    fun observeReEmitsAfterEachWrite() = runTest {
        val emissions = Channel<List<ItemEntity>>(Channel.UNLIMITED)
        backgroundScope.launch(Dispatchers.Default) {
            dao.observeItems("list1").collect { emissions.send(it) }
        }
        assertEquals(0, emissions.receive().size)

        dao.upsert(item("a"))
        assertEquals(1, emissions.receive().size)

        dao.deleteById("a")
        assertEquals(0, emissions.receive().size)
    }

    @Test
    fun deleteByIdsRemovesOnlyThoseRows() = runTest {
        dao.upsertAll(listOf(item("a"), item("b"), item("c")))

        dao.deleteByIds(listOf("a", "c"))

        assertEquals(listOf("b"), dao.getItems("list1").map { it.id })
        dao.deleteById("b")
        assertNull(dao.getById("b"))
    }

    @Test
    fun deleteCheckedOnlyAffectsCheckedItemsOfThatList() = runTest {
        dao.upsertAll(
            listOf(
                item("a", checked = true),
                item("b", checked = false),
                item("c", listId = "other", checked = true),
            ),
        )

        assertEquals(listOf("a"), dao.getCheckedIds("list1"))
        dao.deleteChecked("list1")

        assertEquals(listOf("b"), dao.getItems("list1").map { it.id })
        assertEquals(listOf("c"), dao.getItems("other").map { it.id })
    }
}
