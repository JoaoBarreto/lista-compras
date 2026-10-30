package com.example.listacompras.data.remote.dto

import com.example.listacompras.data.remote.PocketBaseInstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Mirror of the PocketBase `items` record.
 *
 * Optional fields default to null so they are omitted when sending a create request
 * (`encodeDefaults` is off). PocketBase returns empty strings for unset text/relation/select
 * fields; mapping those to domain nulls is the repository's job.
 */
@Serializable
data class ItemDto(
    val id: String,
    val list: String,
    val name: String,
    val quantity: String? = null,
    val checked: Boolean,
    val category: String? = null,
    @SerialName("added_by") val addedBy: String,
    @SerialName("checked_by") val checkedBy: String? = null,
    @Serializable(with = PocketBaseInstantSerializer::class) val created: Instant? = null,
    @Serializable(with = PocketBaseInstantSerializer::class) val updated: Instant? = null,
)
