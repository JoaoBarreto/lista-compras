package com.example.listacompras.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class OpType { CREATE, UPDATE, DELETE }

/**
 * Outbox row. Deliberately has no foreign key to `items`: a DELETE op must outlive the
 * item row it refers to.
 */
@Entity(tableName = "pending_ops", indices = [Index("itemId")])
data class PendingOpEntity(
    @PrimaryKey(autoGenerate = true) val opId: Long = 0,
    val itemId: String,
    val type: OpType,
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int = 0,
)
