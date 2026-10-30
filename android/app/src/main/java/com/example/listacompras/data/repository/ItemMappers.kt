package com.example.listacompras.data.repository

import com.example.listacompras.data.local.entity.ItemEntity
import com.example.listacompras.data.remote.dto.ItemDto
import com.example.listacompras.domain.model.Category
import com.example.listacompras.domain.model.ShoppingItem
import com.example.listacompras.domain.model.SyncState
import java.time.Instant

internal fun ItemEntity.toDomain() = ShoppingItem(
    id = id,
    listId = listId,
    name = name,
    quantity = quantity?.takeIf { it.isNotBlank() },
    checked = checked,
    category = Category.fromSlug(category),
    addedBy = addedBy,
    checkedBy = checkedBy?.takeIf { it.isNotBlank() },
    updatedAt = Instant.ofEpochMilli(updatedAt),
    syncState = syncState,
)

/** The body 4.1 will POST for this item. `created`/`updated` stay null so they are omitted. */
internal fun ItemEntity.toCreateDto() = ItemDto(
    id = id,
    list = listId,
    name = name,
    quantity = quantity,
    checked = checked,
    category = category,
    addedBy = addedBy,
)

/** Server record → local row. PocketBase sends `""` for unset optional fields; those become null. */
internal fun ItemDto.toEntity(): ItemEntity {
    val createdMillis = (created ?: updated ?: Instant.EPOCH).toEpochMilli()
    return ItemEntity(
        id = id,
        listId = list,
        name = name,
        quantity = quantity?.takeIf { it.isNotBlank() },
        checked = checked,
        category = category?.takeIf { it.isNotBlank() },
        addedBy = addedBy,
        checkedBy = checkedBy?.takeIf { it.isNotBlank() },
        createdAt = createdMillis,
        updatedAt = updated?.toEpochMilli() ?: createdMillis,
        syncState = SyncState.SYNCED,
    )
}
