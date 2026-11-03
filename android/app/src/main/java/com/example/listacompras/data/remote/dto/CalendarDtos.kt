package com.example.listacompras.data.remote.dto

import com.example.listacompras.data.remote.PocketBaseInstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** Mirror of the PocketBase `event_types` record. */
@Serializable
data class EventTypeDto(
    val id: String,
    val name: String,
    val color: String,
    @Serializable(with = PocketBaseInstantSerializer::class) val created: Instant? = null,
    @Serializable(with = PocketBaseInstantSerializer::class) val updated: Instant? = null,
)

/** Mirror of the PocketBase `events` record. Unset optional fields come back as `""`. */
@Serializable
data class EventDto(
    val id: String,
    val title: String,
    val notes: String? = null,
    @SerialName("starts_at") @Serializable(with = PocketBaseInstantSerializer::class) val startsAt: Instant? = null,
    @SerialName("all_day") val allDay: Boolean = false,
    val type: String,
    val reminder: String? = null,
    @SerialName("created_by") val createdBy: String,
    @Serializable(with = PocketBaseInstantSerializer::class) val created: Instant? = null,
    @Serializable(with = PocketBaseInstantSerializer::class) val updated: Instant? = null,
)
