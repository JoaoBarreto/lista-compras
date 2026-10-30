package com.example.listacompras.domain.repository

interface ListRepository {

    /**
     * Makes sure the active list is known locally, fetching it from the server only when missing.
     * Returns true when a list is available. Network failures return false and are safe to retry.
     */
    suspend fun ensureActiveList(): Boolean
}
