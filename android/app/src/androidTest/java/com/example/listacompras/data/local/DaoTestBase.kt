package com.example.listacompras.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.listacompras.data.local.entity.ItemEntity
import com.example.listacompras.domain.model.SyncState
import org.junit.After
import org.junit.Before

/** In-memory database, so the app's real database is never touched. */
abstract class DaoTestBase {

    protected lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun closeDb() = db.close()

    protected fun item(
        id: String,
        listId: String = "list1",
        createdAt: Long = 0,
        checked: Boolean = false,
    ) = ItemEntity(
        id = id,
        listId = listId,
        name = "Item $id",
        quantity = null,
        checked = checked,
        category = null,
        addedBy = "user1",
        checkedBy = null,
        createdAt = createdAt,
        updatedAt = createdAt,
        syncState = SyncState.SYNCED,
    )
}
