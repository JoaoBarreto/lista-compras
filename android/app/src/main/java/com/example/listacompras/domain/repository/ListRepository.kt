package com.example.listacompras.domain.repository

import kotlinx.coroutines.flow.Flow

interface ListRepository {

    /** True once the active list is known locally; adding items needs it. */
    val hasActiveList: Flow<Boolean>

    /**
     * Makes sure the active list is known locally, fetching it from the server only when missing.
     * Returns true when a list is available. Network failures return false and are safe to retry.
     */
    suspend fun ensureActiveList(): Boolean
}
