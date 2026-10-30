package com.example.listacompras.data.repository

import com.example.listacompras.data.local.entity.OpType
import com.example.listacompras.data.local.entity.PendingOpEntity
import com.example.listacompras.data.remote.PocketBaseApiFactory
import com.example.listacompras.data.remote.dto.ItemDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxCoalescerTest {

    private val json = PocketBaseApiFactory.json
    private val dao = FakePendingOpDao()
    private val coalescer = OutboxCoalescer(
        dao,
        object : TransactionRunner { override suspend fun <T> run(block: suspend () -> T): T = block() },
        json,
    )

    private var nextId = 1L
    private fun op(item: String, type: OpType, payload: String = "{}", attempts: Int = 0) =
        PendingOpEntity(nextId++, item, type, payload, createdAt = 0, attempts = attempts)

    private val create = """{"id":"a","list":"l","name":"Leite","checked":false,"added_by":"u"}"""
    private fun check(by: String = "u") = """{"checked":true,"checked_by":"$by"}"""
    private val uncheck = """{"checked":false,"checked_by":""}"""

    private fun List<PendingOpEntity>.summary() = map { "${it.itemId}:${it.type}" }

    @Test
    fun `CREATE plus UPDATE becomes one CREATE with the patch merged`() {
        val result = coalescer.coalesce(listOf(op("a", OpType.CREATE, create), op("a", OpType.UPDATE, check())))

        assertEquals(listOf("a:CREATE"), result.summary())
        val dto = json.decodeFromString<ItemDto>(result.single().payloadJson)
        assertTrue(dto.checked)
        assertEquals("u", dto.checkedBy)
        assertEquals("Leite", dto.name)
    }

    @Test
    fun `CREATE plus check and uncheck omits checked_by`() {
        val result = coalescer.coalesce(
            listOf(op("a", OpType.CREATE, create), op("a", OpType.UPDATE, check()), op("a", OpType.UPDATE, uncheck)),
        )

        val dto = json.decodeFromString<ItemDto>(result.single().payloadJson)
        assertEquals(false, dto.checked)
        assertNull(dto.checkedBy)
        assertTrue("checked_by" !in result.single().payloadJson)
    }

    @Test
    fun `CREATE plus DELETE leaves nothing`() {
        assertTrue(coalescer.coalesce(listOf(op("a", OpType.CREATE, create), op("a", OpType.DELETE))).isEmpty())
    }

    @Test
    fun `CREATE UPDATE DELETE leaves nothing`() {
        val ops = listOf(op("a", OpType.CREATE, create), op("a", OpType.UPDATE, check()), op("a", OpType.DELETE))
        assertTrue(coalescer.coalesce(ops).isEmpty())
    }

    @Test
    fun `UPDATE plus UPDATE becomes one UPDATE where the last value wins`() {
        val first = op("a", OpType.UPDATE, check())
        val result = coalescer.coalesce(listOf(first, op("a", OpType.UPDATE, uncheck)))

        assertEquals(listOf(first.opId), result.map { it.opId })
        assertEquals("""{"checked":false,"checked_by":""}""", result.single().payloadJson)
    }

    @Test
    fun `UPDATE plus DELETE leaves only the DELETE`() {
        val del = op("a", OpType.DELETE).let { listOf(op("a", OpType.UPDATE, check()), it) }
        val result = coalescer.coalesce(del)

        assertEquals(listOf("a:DELETE"), result.summary())
    }

    @Test
    fun `an attempted CREATE is frozen, so its DELETE is kept`() {
        val ops = listOf(op("a", OpType.CREATE, create, attempts = 1), op("a", OpType.UPDATE, check()), op("a", OpType.DELETE))

        assertEquals(listOf("a:CREATE", "a:DELETE"), coalescer.coalesce(ops).summary())
    }

    @Test
    fun `updates after an attempted CREATE are merged among themselves, not into it`() {
        val ops = listOf(
            op("a", OpType.CREATE, create, attempts = 2),
            op("a", OpType.UPDATE, check()),
            op("a", OpType.UPDATE, uncheck),
        )
        val result = coalescer.coalesce(ops)

        assertEquals(listOf("a:CREATE", "a:UPDATE"), result.summary())
        assertEquals(create, result[0].payloadJson)
        assertEquals("""{"checked":false,"checked_by":""}""", result[1].payloadJson)
    }

    @Test
    fun `interleaved items stay independent and keep their order`() {
        val ops = listOf(
            op("a", OpType.CREATE, create),
            op("b", OpType.UPDATE, check()),
            op("a", OpType.UPDATE, check()),
            op("b", OpType.UPDATE, uncheck),
            op("c", OpType.DELETE),
        )
        val result = coalescer.coalesce(ops)

        assertEquals(listOf("a:CREATE", "b:UPDATE", "c:DELETE"), result.summary())
        assertEquals(result.map { it.opId }, result.map { it.opId }.sorted())
    }

    @Test
    fun `an already minimal outbox is unchanged`() {
        val ops = listOf(op("a", OpType.CREATE, create), op("b", OpType.UPDATE, check()), op("c", OpType.DELETE))

        assertEquals(ops, coalescer.coalesce(ops))
    }

    @Test
    fun `coalesceOutbox rewrites the stored rows`() = runTest {
        dao.insert(op("a", OpType.CREATE, create))
        dao.insert(op("a", OpType.UPDATE, check()))
        dao.insert(op("b", OpType.CREATE, create))
        dao.insert(op("b", OpType.DELETE))

        coalescer.coalesceOutbox()

        val rows = dao.getAll()
        assertEquals(listOf("a:CREATE"), rows.summary())
        assertTrue(""""checked":true""" in rows.single().payloadJson)
    }
}
