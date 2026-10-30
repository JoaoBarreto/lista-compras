package com.example.listacompras.data.local

import com.example.listacompras.data.local.entity.SyncMetaEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncMetaDaoTest : DaoTestBase() {

    private val dao get() = db.syncMetaDao()

    @Test
    fun missingKeyReturnsNull() = runTest {
        assertNull(dao.get("lastFullSyncAt"))
    }

    @Test
    fun putThenOverwrite() = runTest {
        dao.put(SyncMetaEntity("lastFullSyncAt", "1"))
        assertEquals("1", dao.get("lastFullSyncAt"))

        dao.put(SyncMetaEntity("lastFullSyncAt", "2"))
        assertEquals("2", dao.get("lastFullSyncAt"))
    }
}
