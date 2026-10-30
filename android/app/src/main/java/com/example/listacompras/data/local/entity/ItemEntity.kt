package com.example.listacompras.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.listacompras.domain.model.SyncState

/**
 * Local mirror of a PocketBase `items` record. Times are epoch millis.
 * [category] keeps the raw server string so an unknown value never breaks a read.
 */
@Entity(tableName = "items", indices = [Index(value = ["listId", "checked"])])
data class ItemEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String,
    val quantity: String?,
    val checked: Boolean,
    val category: String?,
    val addedBy: String,
    val checkedBy: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: SyncState,
)
