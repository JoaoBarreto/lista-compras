package com.example.listacompras.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.listacompras.domain.model.SyncState

/** Local mirror of a PocketBase `event_types` record. [color] is the raw `#RRGGBB` string. */
@Entity(tableName = "event_types")
data class EventTypeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: SyncState,
)
