package com.example.listacompras.data.reminder

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.listacompras.data.local.AppDatabase
import com.example.listacompras.data.local.entity.EventEntity
import com.example.listacompras.data.local.entity.EventTypeEntity
import com.example.listacompras.domain.model.SyncState
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Instant

/**
 * Not a normal test: it leaves an event with a reminder for tomorrow and a real alarm behind, so a
 * person or script can check that the alarm survives an app update or a reboot (`dumpsys alarm`).
 * It does nothing unless run with `-e seedReminder true`.
 */
class SeedReminderForSystemChecksTest {

    @Test
    fun seedsAnEventWithAReminderForTomorrow() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("seedReminder") == "true")
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()
        val scheduler = EntryPointAccessors
            .fromApplication(context.applicationContext, ReminderReceiver.Dependencies::class.java)
            .scheduler()
        runBlocking {
            scheduler.reschedule() // creates the watermark at "now"
            db.eventTypeDao().upsert(EventTypeEntity("seedtype0000001", "Teste", "#2196F3", 1, 1, SyncState.SYNCED))
            val at = Instant.now().plusSeconds(24 * 3600)
            db.eventDao().upsert(
                EventEntity("seedevent000001", "Evento de teste", null, at.toEpochMilli(), false, "seedtype0000001", "at_time", "u", 1, 1, SyncState.SYNCED),
            )
            scheduler.reschedule()
        }
        db.close()
    }
}
