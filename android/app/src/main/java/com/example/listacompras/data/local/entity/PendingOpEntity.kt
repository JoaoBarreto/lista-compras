package com.example.listacompras.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class OpType { CREATE, UPDATE, DELETE }

/**
 * Outbox row. Deliberately has no foreign key: a DELETE op must outlive the row it refers to.
 * [itemId] is the id of the record in [collection] (`items`, `events` or `event_types`).
 */
@Entity(tableName = "pending_ops", indices = [Index("itemId")])
data class PendingOpEntity(
    @PrimaryKey(autoGenerate = true) val opId: Long = 0,
    val itemId: String,
    val type: OpType,
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int = 0,
    @ColumnInfo(defaultValue = "'items'") val collection: String = "items",
)
