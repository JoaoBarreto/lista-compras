package com.example.listacompras.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.listacompras.data.local.dao.ItemDao
import com.example.listacompras.data.local.dao.PendingOpDao
import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.local.entity.ItemEntity
import com.example.listacompras.data.local.entity.PendingOpEntity
import com.example.listacompras.data.local.entity.SyncMetaEntity

@Database(
    entities = [ItemEntity::class, PendingOpEntity::class, SyncMetaEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun pendingOpDao(): PendingOpDao
    abstract fun syncMetaDao(): SyncMetaDao

    companion object {
        const val NAME = "lista_compras.db"
    }
}
