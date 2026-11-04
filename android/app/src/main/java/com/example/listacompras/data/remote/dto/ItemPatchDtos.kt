package com.example.listacompras.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body of the PATCH sent when an item is checked or unchecked. [checkedBy] is an empty string to
 * clear the relation: a null would be omitted (`explicitNulls = false`) and the server would keep
 * the old value.
 */
@Serializable
data class CheckedPatch(
    val checked: Boolean,
    @SerialName("checked_by") val checkedBy: String,
)

/**
 * Body of the PATCH sent when an item is edited. [quantity] is an empty string to clear it (a null
 * is omitted, so the server would keep the old value) and null only when the list has no quantities.
 */
@Serializable
data class EditPatch(
    val name: String,
    val quantity: String? = null,
)
