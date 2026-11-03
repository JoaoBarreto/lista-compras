package com.example.listacompras.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.listacompras.data.local.entity.PendingOpEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingOpDao {

    /** Returns the generated `opId`. */
    @Insert
    suspend fun insert(op: PendingOpEntity): Long

    @Query("SELECT * FROM pending_ops ORDER BY opId")
    suspend fun getAll(): List<PendingOpEntity>

    @Query("SELECT * FROM pending_ops WHERE collection = :collection AND itemId = :itemId ORDER BY opId")
    suspend fun getForItem(collection: String, itemId: String): List<PendingOpEntity>

    @Query("DELETE FROM pending_ops WHERE opId = :opId")
    suspend fun delete(opId: Long)

    @Query("UPDATE pending_ops SET payloadJson = :payloadJson WHERE opId = :opId")
    suspend fun updatePayload(opId: Long, payloadJson: String)

    @Query("DELETE FROM pending_ops WHERE collection = :collection AND itemId = :itemId")
    suspend fun deleteForItem(collection: String, itemId: String)

    @Query("UPDATE pending_ops SET attempts = attempts + 1 WHERE opId = :opId")
    suspend fun incrementAttempts(opId: Long)

    @Query("SELECT COUNT(*) FROM pending_ops")
    fun observeCount(): Flow<Int>

    /** Records of [collection] that must not be overwritten by a pull because they have unsent changes. */
    @Query("SELECT DISTINCT itemId FROM pending_ops WHERE collection = :collection")
    suspend fun getPendingItemIds(collection: String): List<String>
}
