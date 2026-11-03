package com.example.listacompras.data.sync

import com.example.listacompras.data.local.entity.EventEntity
import com.example.listacompras.data.local.entity.EventTypeEntity
import com.example.listacompras.data.local.entity.OpType
import com.example.listacompras.data.local.entity.PendingOpEntity
import com.example.listacompras.data.remote.PocketBaseApiFactory
import com.example.listacompras.data.remote.TokenProvider
import com.example.listacompras.data.repository.FakeActiveList
import com.example.listacompras.data.repository.FakeEventDao
import com.example.listacompras.data.repository.FakeEventTypeDao
import com.example.listacompras.data.repository.FakeItemDao
import com.example.listacompras.data.repository.FakePendingOpDao
import com.example.listacompras.data.repository.FakeSyncMetaDao
import com.example.listacompras.data.repository.TransactionRunner
import com.example.listacompras.domain.model.ListKind
import com.example.listacompras.domain.model.SyncState
import com.example.listacompras.domain.repository.ListRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Sync of `events` and `event_types` through the shared engine; items must be unaffected. */
class CalendarSyncTest {

    private val json = PocketBaseApiFactory.json
    private val itemDao = FakeItemDao()
    private val eventDao = FakeEventDao()
    private val typeDao = FakeEventTypeDao()
    private val opDao = FakePendingOpDao()
    private val metaDao = FakeSyncMetaDao()
    private val api = FakePocketBaseApi()
    private val logs = mutableListOf<String>()
    private val activeList = FakeActiveList("list1")
    private val tx = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }
    private val lists = object : ListRepository {
        override fun hasList(kind: ListKind) = activeList.observeListId(kind).map { !it.isNullOrEmpty() }
        override suspend fun ensureLists() = true
    }
    private val clock = Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC)
    private val processor = OutboxProcessor(
        opDao, itemDao, eventDao, typeDao, metaDao, tx, OutboxCoalescer(opDao, tx, json), api,
        TokenProvider { "token" }, json, log = { logs += it }, lock = SyncLock(),
    )
    private val reconciler = Reconciler(
        api, itemDao, opDao, metaDao, activeList, lists, tx, SyncLock(), TokenProvider { "token" }, clock,
        typeDao, eventDao, json, log = { logs += it },
    )
    private val applier = RealtimeEventApplier(itemDao, typeDao, eventDao, opDao, activeList, tx, SyncLock(), json) { logs += it }

    private fun obj(text: String) = json.parseToJsonElement(text) as JsonObject
    private fun typeJson(id: String, name: String = id) =
        """{"id":"$id","name":"$name","color":"#4CAF50","created":"2026-10-29 12:00:00.000Z","updated":"2026-10-29 12:00:00.000Z"}"""
    private fun eventJson(id: String, title: String = id) =
        """{"id":"$id","title":"$title","notes":"","starts_at":"2026-11-10 09:00:00.000Z","all_day":false,"type":"t1",""" +
            """"reminder":"","created_by":"u","created":"2026-10-29 12:00:00.000Z","updated":"2026-10-29 12:00:00.000Z"}"""

    private fun localType(id: String, state: SyncState = SyncState.SYNCED) =
        EventTypeEntity(id, id, "#4CAF50", 1, 1, state)

    private fun localEvent(id: String, state: SyncState = SyncState.SYNCED) =
        EventEntity(id, id, null, 1, false, "t1", null, "u", 1, 1, state)

    private fun op(collection: String, id: String, type: OpType, payload: String = "{}") =
        PendingOpEntity(itemId = id, type = type, payloadJson = payload, createdAt = 0, collection = collection)

    // --- outbox ---

    @Test
    fun `an event CREATE is sent to the events collection and the row becomes synced`() = runTest {
        eventDao.upsert(localEvent("e1", SyncState.PENDING))
        opDao.insert(op(SyncCollections.EVENTS, "e1", OpType.CREATE, eventJson("e1")))

        assertEquals(SyncOutcome.Done, processor.process())

        assertNotNull(api.records[SyncCollections.EVENTS]?.get("e1"))
        assertEquals(SyncState.SYNCED, eventDao.getById("e1")!!.syncState)
        assertTrue(opDao.getAll().isEmpty())
    }

    @Test
    fun `event type UPDATE and DELETE reach the server`() = runTest {
        api.records.getOrPut(SyncCollections.EVENT_TYPES) { linkedMapOf() }["t1"] = obj(typeJson("t1"))
        opDao.insert(op(SyncCollections.EVENT_TYPES, "t1", OpType.UPDATE, """{"name":"Novo"}"""))
        processor.process()
        assertEquals("Novo", (api.records.getValue(SyncCollections.EVENT_TYPES).getValue("t1")["name"] as kotlinx.serialization.json.JsonPrimitive).content)

        opDao.insert(op(SyncCollections.EVENT_TYPES, "t1", OpType.DELETE))
        processor.process()
        assertNull(api.records.getValue(SyncCollections.EVENT_TYPES)["t1"])
    }

    @Test
    fun `a retried event CREATE whose response was lost is not duplicated`() = runTest {
        opDao.insert(op(SyncCollections.EVENTS, "e1", OpType.CREATE, eventJson("e1")))
        api.failures += FakePocketBaseApi.Failure.ResponseLost

        assertEquals(SyncOutcome.Retry, processor.process())
        assertEquals(SyncOutcome.Done, processor.process())

        assertEquals(1, api.records.getValue(SyncCollections.EVENTS).size)
    }

    @Test
    fun `ops with the same id in different collections are never merged`() {
        val coalescer = OutboxCoalescer(opDao, tx, json)
        val ops = listOf(
            op(SyncCollections.ITEMS, "x", OpType.CREATE).copy(opId = 1),
            op(SyncCollections.EVENTS, "x", OpType.DELETE).copy(opId = 2),
        )

        assertEquals(listOf(1L, 2L), coalescer.coalesce(ops).map { it.opId })
    }

    @Test
    fun `an empty checked_by is only dropped for items`() {
        val coalescer = OutboxCoalescer(opDao, tx, json)
        val ops = listOf(
            op(SyncCollections.EVENTS, "x", OpType.CREATE, """{"id":"x"}""").copy(opId = 1),
            op(SyncCollections.EVENTS, "x", OpType.UPDATE, """{"checked_by":""}""").copy(opId = 2),
        )

        assertEquals("""{"id":"x","checked_by":""}""", coalescer.coalesce(ops).single().payloadJson)
    }

    // --- pull ---

    @Test
    fun `pull stores calendar records, keeps pending ones and removes the stale`() = runTest {
        api.records[SyncCollections.EVENT_TYPES] = linkedMapOf("t1" to obj(typeJson("t1", "Remoto")))
        api.records[SyncCollections.EVENTS] = linkedMapOf("e1" to obj(eventJson("e1", "Remoto")), "e2" to obj(eventJson("e2")))
        typeDao.upsert(localType("old"))
        eventDao.upsert(localEvent("e2", SyncState.PENDING).copy(title = "Local"))
        eventDao.upsert(localEvent("gone"))
        opDao.insert(op(SyncCollections.EVENTS, "e2", OpType.UPDATE))

        assertEquals(SyncOutcome.Done, reconciler.pull())

        assertEquals(listOf("t1"), typeDao.getAll().map { it.id })
        assertEquals("Remoto", typeDao.getById("t1")!!.name)
        assertEquals(setOf("e1", "e2"), eventDao.getAll().map { it.id }.toSet())
        assertEquals("Local", eventDao.getById("e2")!!.title)
        assertNull(eventDao.getById("gone"))
    }

    @Test
    fun `a missing or denied calendar collection does not fail the items pull`() = runTest {
        api.listFailures[SyncCollections.EVENTS] = 404
        api.listFailures[SyncCollections.EVENT_TYPES] = 403
        typeDao.upsert(localType("keep"))

        assertEquals(SyncOutcome.Done, reconciler.pull())

        assertEquals(listOf("keep"), typeDao.getAll().map { it.id })
        assertTrue(logs.any { it.contains("HTTP 404") })
    }

    // --- realtime ---

    @Test
    fun `realtime events are routed by topic`() = runTest {
        applier.apply("""{"action":"create","record":${typeJson("t1")}}""", "event_types/*")
        applier.apply("""{"action":"create","record":${eventJson("e1")}}""", "events/*")
        assertNotNull(typeDao.getById("t1"))
        assertNotNull(eventDao.getById("e1"))

        applier.apply("""{"action":"delete","record":${eventJson("e1")}}""", "events/*")
        assertNull(eventDao.getById("e1"))
    }

    @Test
    fun `a realtime event for a record with pending ops is ignored and bad input never throws`() = runTest {
        eventDao.upsert(localEvent("e1", SyncState.PENDING).copy(title = "Local"))
        opDao.insert(op(SyncCollections.EVENTS, "e1", OpType.UPDATE))

        applier.apply("""{"action":"update","record":${eventJson("e1", "Remoto")}}""", "events/*")
        applier.apply("not json", "events/*")
        applier.apply("""{"action":"update","record":{"id":"e9","title":"x"}}""", "events/*")

        assertEquals("Local", eventDao.getById("e1")!!.title)
        assertNull(eventDao.getById("e9"))
    }

    @Test
    fun `pull and realtime apply events created by the other user`() = runTest {
        val other = eventJson("e1", "Do outro").replace("\"created_by\":\"u\"", "\"created_by\":\"other\"")
        api.records[SyncCollections.EVENTS] = linkedMapOf("e1" to obj(other))
        reconciler.pull()
        assertEquals("other", eventDao.getById("e1")!!.createdBy)

        applier.apply("""{"action":"create","record":${other.replace("e1", "e2")}}""", "events/*")
        assertEquals("other", eventDao.getById("e2")!!.createdBy)
    }

    @Test
    fun `an item pending op does not protect an event with the same id`() = runTest {
        opDao.insert(op(SyncCollections.ITEMS, "e1", OpType.UPDATE))

        applier.apply("""{"action":"create","record":${eventJson("e1")}}""", "events/*")

        assertNotNull(eventDao.getById("e1"))
    }
}
