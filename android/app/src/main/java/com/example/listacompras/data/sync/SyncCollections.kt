package com.example.listacompras.data.sync

/** PocketBase collection names used as the `collection` of a pending operation. */
object SyncCollections {
    const val ITEMS = "items"
    const val EVENTS = "events"
    const val EVENT_TYPES = "event_types"

    /** Collections handled besides `items` (which has its own list-scoped DTO and endpoints). */
    val CALENDAR = listOf(EVENT_TYPES, EVENTS)

    /** Realtime topic of a collection, the collection name followed by a slash and a star. */
    fun topic(collection: String) = "$collection/*"
}
