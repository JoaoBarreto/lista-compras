package com.example.listacompras.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.listacompras.data.local.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query("SELECT * FROM items WHERE listId = :listId ORDER BY createdAt, id")
    fun observeItems(listId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE listId = :listId ORDER BY createdAt, id")
    suspend fun getItems(listId: String): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: String): ItemEntity?

    @Upsert
    suspend fun upsert(item: ItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<ItemEntity>)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT id FROM items WHERE listId = :listId AND checked = 1")
    suspend fun getCheckedIds(listId: String): List<String>

    @Query("DELETE FROM items WHERE listId = :listId AND checked = 1")
    suspend fun deleteChecked(listId: String)
}
