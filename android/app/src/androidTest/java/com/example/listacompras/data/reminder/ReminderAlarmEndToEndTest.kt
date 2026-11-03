package com.example.listacompras.data.reminder

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.listacompras.data.local.AppDatabase
import com.example.listacompras.data.local.entity.EventEntity
import com.example.listacompras.data.local.entity.EventTypeEntity
import com.example.listacompras.domain.model.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * The real thing, on the phone: a real alarm is set with the system AlarmManager, the system fires
 * [ReminderReceiver], and the receiver reads the app's own database and posts the notification.
 * It uses the `.debug` app, so the data of the release app is never touched.
 */
class ReminderAlarmEndToEndTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alarms = AndroidReminderAlarms(context)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private lateinit var db: AppDatabase
    private val eventId = "e2ealarmtest001"

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        // The same file the app uses; the receiver reads it when the alarm fires.
        db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()
        alarms.cancel()
        notifications.cancelAll()
        runBlocking {
            db.eventTypeDao().upsert(EventTypeEntity("e2etypetest0001", "Teste", "#2196F3", 1, 1, SyncState.SYNCED))
        }
    }

    @After
    fun tearDown() {
        alarms.cancel()
        notifications.cancelAll()
        runBlocking {
            db.eventDao().deleteById(eventId)
            db.eventTypeDao().deleteById("e2etypetest0001")
        }
        db.close()
    }

    /** Asks the system itself (`dumpsys alarm`) whether it holds an alarm of this app. */
    private fun alarmIsSet(): Boolean {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys alarm")
        val text = java.io.FileInputStream(output.fileDescriptor).bufferedReader().use { it.readText() }
        return text.lineSequence().any { it.contains("${context.packageName}") && it.contains("Alarm{") }
    }

    @Test
    fun aRealAlarmFiresTheReceiverWhichPostsTheReminderNotification() {
        val at = Instant.now().plusSeconds(4)
        runBlocking {
            // The watermark must be before the reminder, as it is in real use.
            db.syncMetaDao().put(
                com.example.listacompras.data.local.entity.SyncMetaEntity(
                    "reminderWatermark",
                    Instant.now().minusSeconds(60).toEpochMilli().toString(),
                ),
            )
            db.eventDao().upsert(
                EventEntity(eventId, "Lembrete de teste", null, at.toEpochMilli(), false, "e2etypetest0001", "at_time", "u", 1, 1, SyncState.SYNCED),
            )
        }

        alarms.scheduleAt(at)
        assertTrue("the alarm is registered with the system", alarmIsSet())

        val deadline = System.currentTimeMillis() + 30_000
        var posted = false
        while (System.currentTimeMillis() < deadline && !posted) {
            Thread.sleep(500)
            posted = notifications.activeNotifications.any {
                it.notification.extras.getString("android.title") == "Lembrete de teste"
            }
        }
        assertTrue("the notification for the event was posted after the alarm fired", posted)
    }

    @Test
    fun cancellingRemovesTheAlarm() {
        alarms.scheduleAt(Instant.now().plusSeconds(3600))
        assertTrue("the system holds the alarm", alarmIsSet())
        alarms.cancel()
        assertFalse("the system no longer holds it after cancel", alarmIsSet())
    }
}
