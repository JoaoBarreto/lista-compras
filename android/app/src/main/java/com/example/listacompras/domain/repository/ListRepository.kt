package com.example.listacompras.domain.repository

import com.example.listacompras.domain.model.ListKind
import kotlinx.coroutines.flow.Flow

interface ListRepository {

    /** True once the server id of the list is known locally; adding items needs it. */
    fun hasList(kind: ListKind): Flow<Boolean>

    /**
     * Makes sure the lists are known locally, asking the server only for those still missing.
     * Returns true when the shopping list is available; a missing tasks list does not matter here.
     * Network failures return false (or true if shopping is already known) and are safe to retry.
     */
    suspend fun ensureLists(): Boolean
}
