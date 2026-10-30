package com.example.listacompras.data.remote.dto

import kotlinx.serialization.Serializable

/** Paginated list response returned by `GET /api/collections/{collection}/records`. */
@Serializable
data class ListResponse<T>(
    val page: Int,
    val perPage: Int,
    val totalPages: Int,
    val totalItems: Int,
    val items: List<T>,
)
