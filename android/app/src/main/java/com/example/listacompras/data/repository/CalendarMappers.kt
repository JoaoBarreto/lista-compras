package com.example.listacompras.data.repository

import com.example.listacompras.data.local.entity.EventEntity
import com.example.listacompras.data.local.entity.EventTypeEntity
import com.example.listacompras.data.remote.dto.EventDto
import com.example.listacompras.data.remote.dto.EventTypeDto
import com.example.listacompras.domain.model.SyncState
import java.time.Instant

/** Server record → local row. PocketBase sends `""` for unset optional fields; those become null. */
internal fun EventTypeDto.toEntity(): EventTypeEntity {
    val createdMillis = (created ?: updated ?: Instant.EPOCH).toEpochMilli()
    return EventTypeEntity(
        id = id,
        name = name,
        color = color,
        createdAt = createdMillis,
        updatedAt = updated?.toEpochMilli() ?: createdMillis,
        syncState = SyncState.SYNCED,
    )
}

internal fun EventDto.toEntity(): EventEntity {
    val createdMillis = (created ?: updated ?: Instant.EPOCH).toEpochMilli()
    return EventEntity(
        id = id,
        title = title,
        notes = notes?.takeIf { it.isNotBlank() },
        startsAt = requireNotNull(startsAt) { "starts_at missing" }.toEpochMilli(),
        allDay = allDay,
        typeId = type,
        reminder = reminder?.takeIf { it.isNotBlank() },
        createdBy = createdBy,
        createdAt = createdMillis,
        updatedAt = updated?.toEpochMilli() ?: createdMillis,
        syncState = SyncState.SYNCED,
    )
}
