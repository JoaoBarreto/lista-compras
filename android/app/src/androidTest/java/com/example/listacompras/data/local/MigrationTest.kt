package com.example.listacompras.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2KeepsV1DataAndAddsCalendarTables() {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO items (id, listId, name, quantity, checked, category, addedBy, checkedBy, createdAt, updatedAt, syncState) " +
                    "VALUES ('i1', 'l1', 'Leite', '2', 0, NULL, 'u', NULL, 1, 2, 'PENDING')",
            )
            execSQL("INSERT INTO pending_ops (itemId, type, payloadJson, createdAt, attempts) VALUES ('i1', 'CREATE', '{}', 5, 1)")
            execSQL("INSERT INTO sync_meta (`key`, value) VALUES ('lastFullSyncAt', '9')")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

        db.query("SELECT name, quantity, syncState FROM items WHERE id = 'i1'").use {
            it.moveToFirst()
            assertEquals("Leite", it.getString(0))
            assertEquals("2", it.getString(1))
            assertEquals("PENDING", it.getString(2))
        }
        db.query("SELECT itemId, type, attempts, collection FROM pending_ops").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("i1", it.getString(0))
            assertEquals("CREATE", it.getString(1))
            assertEquals(1, it.getInt(2))
            assertEquals("items", it.getString(3))
        }
        db.query("SELECT value FROM sync_meta WHERE `key` = 'lastFullSyncAt'").use {
            it.moveToFirst()
            assertEquals("9", it.getString(0))
        }
        db.query("SELECT COUNT(*) FROM events").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM event_types").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
