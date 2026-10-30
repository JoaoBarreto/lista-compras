package com.example.listacompras.domain.model

import java.time.Instant

data class ShoppingItem(
    val id: String,
    val listId: String,
    val name: String,
    val quantity: String?,
    val checked: Boolean,
    val category: Category?,
    val addedBy: String,
    val checkedBy: String?,
    val updatedAt: Instant,
    val syncState: SyncState,
)
