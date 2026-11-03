package com.example.listacompras.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.listacompras.domain.model.SyncState

/**
 * Local mirror of a PocketBase `events` record. Times are epoch millis. [reminder] keeps the raw
 * server value (`at_time`, `1h`, `1d`) or null for none.
 */
@Entity(tableName = "events", indices = [Index("startsAt")])
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String?,
    val startsAt: Long,
    val allDay: Boolean,
    val typeId: String,
    val reminder: String?,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: SyncState,
)
