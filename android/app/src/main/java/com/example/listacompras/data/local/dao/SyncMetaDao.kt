package com.example.listacompras.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.listacompras.data.local.entity.SyncMetaEntity

@Dao
interface SyncMetaDao {

    @Query("SELECT value FROM sync_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(meta: SyncMetaEntity)
}
