package com.example.listacompras.data.notify

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.listacompras.data.local.AppDatabase
import com.example.listacompras.data.local.entity.EventEntity
import com.example.listacompras.data.local.entity.ItemEntity
import com.example.listacompras.data.repository.CurrentUserProvider
import com.example.listacompras.data.repository.SyncMetaActiveListProvider
import com.example.listacompras.domain.model.ListKind
import com.example.listacompras.domain.model.SyncState
import com.example.listacompras.domain.repository.EventNotificationSettings
import com.example.listacompras.domain.repository.NotificationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * "The other user" is simulated by records with another author and a later server time. The real
 * checkers and the real Android notifiers run on the phone over an in-memory database; the
 * notifications are read back from the system.
 */
class NewRecordsNotificationOnDeviceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val me = object : CurrentUserProvider { override fun currentUserId() = "me" }
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        notifications.cancelAll()
    }

    @After
    fun tearDown() {
        notifications.cancelAll()
        db.close()
    }

    private fun titles(id: Int) = notifications.activeNotifications.filter { it.id == id }
        .map { it.notification.extras.getString("android.title").orEmpty() + " | " + it.notification.extras.getCharSequence("android.text") }

    @Test
    fun anEventFromTheOtherUserShowsOneNotificationUntilTheAppIsOpened() = runBlocking {
        val checker = EventNotificationChecker(
            db.eventDao(), db.syncMetaDao(), me,
            object : EventNotificationSettings {
                override val eventsEnabled: Flow<Boolean> = flowOf(true)
                override suspend fun setEventsEnabled(enabled: Boolean) = Unit
            },
            AndroidNewEventsNotifier(context),
            now = { 1_000L },
        )
        checker.check() // first run: silent
        db.eventDao().upsert(EventEntity("ev1", "Jantar com os pais", null, 0, false, "t1", null, "other", 2_000, 2_000, SyncState.SYNCED))
        db.eventDao().upsert(EventEntity("ev2", "O meu evento", null, 0, false, "t1", null, "me", 3_000, 3_000, SyncState.SYNCED))

        checker.check()
        checker.check() // no repetition

        val shown = titles(AndroidNewEventsNotifier.NOTIFICATION_ID)
        assertEquals(1, shown.size)
        assertTrue(shown.single(), shown.single().contains("Jantar com os pais"))
        assertTrue(shown.single(), !shown.single().contains("O meu evento"))

        checker.onAppVisible()
        assertTrue(titles(AndroidNewEventsNotifier.NOTIFICATION_ID).isEmpty())
    }

    @Test
    fun anItemFromTheOtherUserShowsOneNotificationUntilTheAppIsOpened() = runBlocking {
        val activeList = SyncMetaActiveListProvider(db.syncMetaDao())
        activeList.setListId(ListKind.SHOPPING, "list1")
        val checker = ShoppingNotificationChecker(
            db.itemDao(), db.syncMetaDao(), activeList, me,
            object : NotificationSettings {
                override val shoppingEnabled: Flow<Boolean> = flowOf(true)
                override suspend fun setShoppingEnabled(enabled: Boolean) = Unit
                override suspend fun permissionAsked() = true
                override suspend fun markPermissionAsked() = Unit
            },
            AndroidNewItemsNotifier(context),
        )
        checker.check() // first run: silent
        db.itemDao().upsert(ItemEntity("it1", "list1", "Leite", null, false, null, "other", null, 2_000, 2_000, SyncState.SYNCED))

        checker.check()
        checker.check()

        val shown = titles(1001)
        assertEquals(1, shown.size)
        assertTrue(shown.single(), shown.single().contains("Leite"))

        checker.onAppVisible()
        assertTrue(titles(1001).isEmpty())
    }
}
