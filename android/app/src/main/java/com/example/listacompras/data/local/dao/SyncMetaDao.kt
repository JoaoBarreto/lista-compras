package com.example.listacompras.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.listacompras.data.local.entity.SyncMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetaDao {

    @Query("SELECT value FROM sync_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM sync_meta WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Upsert
    suspend fun put(meta: SyncMetaEntity)
}
