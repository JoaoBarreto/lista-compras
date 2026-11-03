package com.example.listacompras.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SubscribeRequest(
    val clientId: String,
    val subscriptions: List<String>,
)

/** `data` of the `PB_CONNECT` event. */
@Serializable
data class ConnectEvent(val clientId: String)

/** `data` of a record event: `action` is `create`, `update` or `delete`. */
@Serializable
data class RealtimeRecordEvent(
    val action: String,
    val record: ItemDto,
)

/** Record event of a calendar collection: the record stays raw until the topic says how to decode it. */
@Serializable
data class RealtimeRawEvent(
    val action: String,
    val record: JsonObject,
)
