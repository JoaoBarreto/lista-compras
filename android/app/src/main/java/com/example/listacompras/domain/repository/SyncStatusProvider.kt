package com.example.listacompras.domain.repository

import com.example.listacompras.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

interface SyncStatusProvider {

    val status: Flow<SyncStatus>

    /** Dismisses the current [SyncStatus.Error] (operations the server rejected). */
    suspend fun acknowledgeErrors()
}
