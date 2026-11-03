package com.example.listacompras.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.listacompras.data.local.dao.EventDao
import com.example.listacompras.data.local.dao.EventTypeDao
import com.example.listacompras.data.local.dao.ItemDao
import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.local.entity.EventEntity
import com.example.listacompras.data.local.entity.EventTypeEntity
import com.example.listacompras.data.local.entity.ItemEntity
import com.example.listacompras.data.local.entity.PendingOpEntity
import com.example.listacompras.data.local.entity.SyncMetaEntity

@Database(
    entities = [
        ItemEntity::class,
        PendingOpEntity::class,
        SyncMetaEntity::class,
        EventTypeEntity::class,
        EventEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun pendingOpDao(): PendingOpDao
    abstract fun syncMetaDao(): SyncMetaDao
    abstract fun eventTypeDao(): EventTypeDao
    abstract fun eventDao(): EventDao

    companion object {
        const val NAME = "lista_compras.db"
    }
}
