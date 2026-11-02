package com.example.listacompras.data.notify

import com.example.listacompras.data.local.entity.ItemEntity
import com.example.listacompras.data.repository.CurrentUserProvider
import com.example.listacompras.data.repository.FakeActiveList
import com.example.listacompras.data.repository.FakeItemDao
import com.example.listacompras.data.repository.FakeSyncMetaDao
import com.example.listacompras.domain.model.SyncState
import com.example.listacompras.domain.repository.NotificationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingNotificationCheckerTest {

    private class FakeNotifier : NewItemsNotifier {
        val shown = mutableListOf<List<String>>()
        var cancels = 0
        override fun show(names: List<String>) { shown += names }
        override fun cancel() { cancels++ }
    }

    private class FakeSettings(enabled: Boolean = true) : NotificationSettings {
        val enabled = MutableStateFlow(enabled)
        override val shoppingEnabled: Flow<Boolean> = this.enabled
        override suspend fun setShoppingEnabled(enabled: Boolean) { this.enabled.value = enabled }
        override suspend fun permissionAsked() = false
        override suspend fun markPermissionAsked() {}
    }

    private val itemDao = FakeItemDao()
    private val meta = FakeSyncMetaDao()
    private val notifier = FakeNotifier()
    private val settings = FakeSettings()
    private val checker = ShoppingNotificationChecker(
        itemDao, meta, FakeActiveList("list1"),
        object : CurrentUserProvider { override fun currentUserId() = "me" },
        settings, notifier,
    )

    private suspend fun item(
        id: String, by: String = "other", at: Long, state: SyncState = SyncState.SYNCED, list: String = "list1",
    ) = itemDao.upsert(
        ItemEntity(id, list, "name-$id", null, false, null, by, null, at, at, state),
    )

    @Test
    fun `first run starts silently at the newest item`() = runTest {
        item("a", at = 100)
        checker.check()
        assertTrue(notifier.shown.isEmpty())
        assertEquals("100", meta.values.value["lastSeenItemAt"])
        assertEquals("100", meta.values.value["lastNotifiedItemAt"])
    }

    @Test
    fun `items from the other user are notified once, grouped`() = runTest {
        item("a", at = 100)
        checker.check()
        item("b", at = 200); item("c", at = 300)

        checker.check()
        checker.check()

        assertEquals(listOf(listOf("name-b", "name-c")), notifier.shown)
    }

    @Test
    fun `a later check lists everything unseen, including earlier notified items`() = runTest {
        checker.check()
        item("b", at = 200)
        checker.check()
        item("c", at = 300)
        checker.check()

        assertEquals(listOf(listOf("name-b"), listOf("name-b", "name-c")), notifier.shown)
    }

    @Test
    fun `own items, pending items and other lists are never notified`() = runTest {
        checker.check()
        item("mine", by = "me", at = 200)
        item("pending", at = 300, state = SyncState.PENDING)
        item("elsewhere", at = 400, list = "tasks")

        checker.check()

        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `a pending local item does not push the marker past the other users items`() = runTest {
        checker.check()
        item("mine", by = "me", at = 9_999, state = SyncState.PENDING) // phone clock ahead of the server
        item("b", at = 200)

        checker.check()

        assertEquals(listOf(listOf("name-b")), notifier.shown)
    }

    @Test
    fun `items seen while the app was visible are not notified`() = runTest {
        checker.check()
        checker.onAppVisible()
        item("b", at = 200) // arrived by realtime and was on screen
        checker.onAppHidden()

        checker.check()

        assertTrue(notifier.shown.isEmpty())
        assertTrue(notifier.cancels >= 2)
    }

    @Test
    fun `nothing is announced while the app is visible`() = runTest {
        checker.check()
        checker.onAppVisible()
        item("b", at = 200)
        checker.check()
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `when disabled nothing is shown and turning it on later does not announce old items`() = runTest {
        checker.check()
        settings.enabled.value = false
        item("b", at = 200)
        checker.check()
        settings.enabled.value = true
        checker.check()

        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `without a shopping list or a user nothing happens`() = runTest {
        val noUser = ShoppingNotificationChecker(
            itemDao, meta, FakeActiveList("list1"),
            object : CurrentUserProvider { override fun currentUserId() = null }, settings, notifier,
        )
        val noList = ShoppingNotificationChecker(
            itemDao, meta, FakeActiveList(null),
            object : CurrentUserProvider { override fun currentUserId() = "me" }, settings, notifier,
        )
        item("a", at = 100)
        noUser.check(); noList.check()
        assertTrue(notifier.shown.isEmpty())
        assertTrue(meta.values.value.isEmpty())
    }
}
