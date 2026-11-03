package com.example.listacompras.data.repository

import com.example.listacompras.data.local.entity.OpType
import com.example.listacompras.data.local.entity.PendingOpEntity
import com.example.listacompras.data.remote.PocketBaseApiFactory
import com.example.listacompras.data.sync.SyncCollections
import com.example.listacompras.domain.model.Reminder
import com.example.listacompras.domain.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CalendarRepositoriesTest {

    private val eventDao = FakeEventDao()
    private val typeDao = FakeEventTypeDao()
    private val opDao = FakePendingOpDao()
    private val scheduler = CountingSyncScheduler()
    private var userId: String? = "user1"
    private var ids = generateSequence(1) { it + 1 }.map { "id%012d".format(it) }.iterator()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)
    private val tx = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }
    private val json = PocketBaseApiFactory.json
    private val startsAt = Instant.parse("2026-12-01T10:00:00Z")

    private val types = EventTypeRepositoryImpl(typeDao, eventDao, opDao, tx, json, clock, scheduler) { ids.next() }
    private val events = EventRepositoryImpl(
        eventDao, typeDao, opDao, tx, object : CurrentUserProvider { override fun currentUserId() = userId },
        json, clock, scheduler,
    ) { ids.next() }

    private suspend fun ops(): List<PendingOpEntity> = opDao.getAll()
    private suspend fun addType() = types.add("  Família ", "#4CAF50").also { assertTrue(it) }.let { typeDao.getAll().single().id }

    // --- event types ---

    @Test
    fun `adding a type stores it as pending and queues a CREATE in event_types`() = runTest {
        assertTrue(types.add("  Família ", "#4CAF50"))

        val type = typeDao.getAll().single()
        assertEquals("Família", type.name)
        assertEquals(SyncState.PENDING, type.syncState)
        val op = ops().single()
        assertEquals(SyncCollections.EVENT_TYPES, op.collection)
        assertEquals(OpType.CREATE, op.type)
        assertEquals(type.id, op.itemId)
        assertEquals("""{"id":"${type.id}","name":"Família","color":"#4CAF50"}""", op.payloadJson)
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun `invalid type names and colours queue nothing`() = runTest {
        assertFalse(types.add("   ", "#4CAF50"))
        assertFalse(types.add("x".repeat(31), "#4CAF50"))
        assertFalse(types.add("Ok", "red"))
        assertFalse(types.add("Ok", "#12345"))
        assertTrue(typeDao.getAll().isEmpty())
        assertTrue(ops().isEmpty())
        assertEquals(0, scheduler.requests)
    }

    @Test
    fun `updating a type changes the row and queues an UPDATE with name and colour`() = runTest {
        val id = addType()

        assertTrue(types.update(id, "Casa", "#2196f3"))

        assertEquals("Casa", typeDao.getById(id)!!.name)
        val op = ops().last()
        assertEquals(SyncCollections.EVENT_TYPES, op.collection)
        assertEquals(OpType.UPDATE, op.type)
        assertEquals("""{"name":"Casa","color":"#2196f3"}""", op.payloadJson)
        assertFalse(types.update("missing", "X", "#2196F3"))
        assertFalse(types.update(id, "", "#2196F3"))
        assertEquals(2, ops().size)
    }

    @Test
    fun `deleting an unused type removes it and queues a DELETE`() = runTest {
        val id = addType()

        assertTrue(types.delete(id))

        assertNull(typeDao.getById(id))
        val op = ops().last()
        assertEquals(SyncCollections.EVENT_TYPES, op.collection)
        assertEquals(OpType.DELETE, op.type)
        assertFalse(types.delete(id))
    }

    @Test
    fun `a type used by an event cannot be deleted`() = runTest {
        val id = addType()
        assertTrue(events.add("Jantar", null, startsAt, false, id, null))
        val before = ops().size

        assertFalse(types.delete(id))

        assertEquals(id, typeDao.getById(id)!!.id)
        assertEquals(before, ops().size)
    }

    // --- events ---

    @Test
    fun `adding an event stores it as pending and queues a CREATE with the full body`() = runTest {
        val typeId = addType()

        assertTrue(events.add(" Jantar ", "  com os pais ", startsAt, false, typeId, Reminder.FIVE_DAYS))

        val event = eventDao.getAll().single()
        assertEquals("Jantar", event.title)
        assertEquals("user1", event.createdBy)
        assertEquals(SyncState.PENDING, event.syncState)
        val op = ops().last()
        assertEquals(SyncCollections.EVENTS, op.collection)
        assertEquals(OpType.CREATE, op.type)
        assertEquals(
            """{"id":"${event.id}","title":"Jantar","notes":"com os pais","starts_at":"2026-12-01 10:00:00.000Z",""" +
                """"type":"$typeId","reminder":"5d","created_by":"user1"}""",
            op.payloadJson,
        )
        assertEquals(2, scheduler.requests)
    }

    @Test
    fun `an invalid event or an unknown type queues nothing`() = runTest {
        val typeId = addType()
        val before = ops().size

        assertFalse(events.add("  ", null, startsAt, false, typeId, null))
        assertFalse(events.add("x".repeat(101), null, startsAt, false, typeId, null))
        assertFalse(events.add("Ok", "n".repeat(501), startsAt, false, typeId, null))
        assertFalse(events.add("Ok", null, startsAt, false, "unknown", null))
        userId = null
        assertFalse(events.add("Ok", null, startsAt, false, typeId, null))

        assertTrue(eventDao.getAll().isEmpty())
        assertEquals(before, ops().size)
    }

    @Test
    fun `updating an event sends every editable field and clears optional ones with an empty string`() = runTest {
        val typeId = addType()
        events.add("Jantar", "notas", startsAt, false, typeId, Reminder.ONE_DAY)
        val id = eventDao.getAll().single().id

        assertTrue(events.update(id, "Almoço", null, startsAt, true, typeId, null))

        val stored = eventDao.getById(id)!!
        assertEquals("Almoço", stored.title)
        assertNull(stored.notes)
        assertNull(stored.reminder)
        assertEquals("user1", stored.createdBy)
        val op = ops().last()
        assertEquals(SyncCollections.EVENTS, op.collection)
        assertEquals(OpType.UPDATE, op.type)
        assertEquals(
            """{"title":"Almoço","notes":"","starts_at":"2026-12-01 10:00:00.000Z","all_day":true,"type":"$typeId","reminder":""}""",
            op.payloadJson,
        )
        assertFalse(events.update("missing", "X", null, startsAt, false, typeId, null))
        assertFalse(events.update(id, "X", null, startsAt, false, "unknown", null))
    }

    @Test
    fun `deleting an event removes it and queues a DELETE in events`() = runTest {
        val typeId = addType()
        events.add("Jantar", null, startsAt, false, typeId, null)
        val id = eventDao.getAll().single().id
        val requests = scheduler.requests

        events.delete(id)

        assertNull(eventDao.getById(id))
        val op = ops().last()
        assertEquals(SyncCollections.EVENTS, op.collection)
        assertEquals(OpType.DELETE, op.type)
        events.delete(id)
        assertEquals(requests + 1, scheduler.requests)
    }

    @Test
    fun `a type and an event created offline are queued in that order`() = runTest {
        val typeId = addType()
        events.add("Jantar", null, startsAt, false, typeId, null)

        assertEquals(
            listOf(SyncCollections.EVENT_TYPES, SyncCollections.EVENTS),
            ops().map { it.collection },
        )
    }

    @Test
    fun `observers map entities to domain models and unknown reminders become null`() = runTest {
        val typeId = addType()
        events.add("Jantar", null, startsAt, false, typeId, Reminder.AT_TIME)
        eventDao.upsert(eventDao.getAll().single().copy(reminder = "10y"))

        assertEquals("Família", types.observeTypes().first().single().name)
        assertNull(events.observeEvents().first().single().reminder)
    }
}
