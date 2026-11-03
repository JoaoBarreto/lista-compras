package com.example.listacompras.domain.model

/** A kind of calendar event, shown with its [color] (`#RRGGBB`) and always also by [name]. */
data class EventType(
    val id: String,
    val name: String,
    val color: String,
    val syncState: SyncState,
)
