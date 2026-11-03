package com.example.listacompras.data.notify

import com.example.listacompras.data.local.entity.EventEntity
import com.example.listacompras.data.repository.CurrentUserProvider
import com.example.listacompras.data.repository.FakeEventDao
import com.example.listacompras.data.repository.FakeSyncMetaDao
import com.example.listacompras.domain.model.SyncState
import com.example.listacompras.domain.repository.EventNotificationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The other user is simulated: events with another `created_by` and their server `created` time. */
class EventNotificationCheckerTest {

    private class FakeNotifier : NewItemsNotifier {
        val shown = mutableListOf<List<String>>()
        var cancels = 0
        override fun show(names: List<String>) { shown += names }
        override fun cancel() { cancels++ }
    }

    private class FakeSettings : EventNotificationSettings {
        val enabled = MutableStateFlow(true)
        override val eventsEnabled: Flow<Boolean> = enabled
        override suspend fun setEventsEnabled(enabled: Boolean) { this.enabled.value = enabled }
    }

    private val eventDao = FakeEventDao()
    private val meta = FakeSyncMetaDao()
    private val notifier = FakeNotifier()
    private val settings = FakeSettings()
    private var now = 1_000L
    private val checker = EventNotificationChecker(
        eventDao, meta,
        object : CurrentUserProvider { override fun currentUserId() = "me" },
        settings, notifier, now = { now },
    )

    private suspend fun event(id: String, by: String = "other", at: Long, state: SyncState = SyncState.SYNCED) =
        eventDao.upsert(EventEntity(id, "evento-$id", null, 0, false, "t1", null, by, at, at, state))

    @Test
    fun `events created by the other user are announced once, grouped`() = runTest {
        event("a", at = 500)
        checker.check() // first run: silent
        event("b", at = 2_000); event("c", at = 3_000)
        now = 4_000

        checker.check()
        checker.check()

        assertEquals(listOf(listOf("evento-b", "evento-c")), notifier.shown)
    }

    @Test
    fun `my own events and pending ones are never announced`() = runTest {
        checker.check()
        event("mine", by = "me", at = 2_000)
        event("pending", at = 3_000, state = SyncState.PENDING)

        checker.check()

        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `history that arrives after a first run with an empty calendar is not announced`() = runTest {
        checker.check() // the calendar pull failed: no events yet, markers start at "now" (1000)
        event("old1", at = 100); event("old2", at = 900) // the whole history arrives later
        now = 5_000

        checker.check()
        assertTrue(notifier.shown.isEmpty())

        event("fresh", at = 6_000) // a really new event from the other user
        checker.check()
        assertEquals(listOf(listOf("evento-fresh")), notifier.shown)
    }

    @Test
    fun `what was on screen is never announced and the notification is cleared`() = runTest {
        checker.check()
        notifier.cancels = 0
        checker.onAppVisible()
        event("seen", at = 2_000)
        checker.check() // visible: counts as seen
        checker.onAppHidden()

        checker.check()

        assertTrue(notifier.shown.isEmpty())
        assertTrue(notifier.cancels >= 1)
    }

    @Test
    fun `with the switch off nothing is announced and turning it on later skips old events`() = runTest {
        checker.check()
        settings.enabled.value = false
        event("a", at = 2_000)
        checker.check()
        settings.enabled.value = true
        checker.check()
        assertTrue(notifier.shown.isEmpty())

        event("b", at = 3_000)
        checker.check()
        assertEquals(listOf(listOf("evento-b")), notifier.shown)
    }

    @Test
    fun `the shopping markers are not touched`() = runTest {
        checker.check()
        event("a", at = 2_000)
        checker.check()
        assertTrue(meta.values.value.keys.none { it.endsWith("ItemAt") })
        assertEquals(setOf("lastSeenEventAt", "lastNotifiedEventAt"), meta.values.value.keys)
    }
}
