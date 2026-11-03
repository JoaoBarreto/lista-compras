package com.example.listacompras.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.listacompras.data.local.entity.EventTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventTypeDao {

    @Query("SELECT * FROM event_types ORDER BY createdAt, id")
    fun observeAll(): Flow<List<EventTypeEntity>>

    @Query("SELECT * FROM event_types ORDER BY createdAt, id")
    suspend fun getAll(): List<EventTypeEntity>

    @Query("SELECT * FROM event_types WHERE id = :id")
    suspend fun getById(id: String): EventTypeEntity?

    @Upsert
    suspend fun upsert(type: EventTypeEntity)

    @Upsert
    suspend fun upsertAll(types: List<EventTypeEntity>)

    @Query("DELETE FROM event_types WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM event_types WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
