package com.example.listacompras.data.remote.dto

import kotlinx.serialization.Serializable

/** Mirror of the PocketBase `lists` record. */
@Serializable
data class ListDto(
    val id: String,
    val name: String? = null,
)
