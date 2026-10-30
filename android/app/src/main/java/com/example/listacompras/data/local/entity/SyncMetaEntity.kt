package com.example.listacompras.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Key/value store for sync bookkeeping, e.g. `lastFullSyncAt`. */
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
