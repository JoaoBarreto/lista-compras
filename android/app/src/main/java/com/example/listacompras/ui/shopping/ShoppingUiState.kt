package com.example.listacompras.ui.shopping

import com.example.listacompras.domain.model.ShoppingItem
import com.example.listacompras.domain.model.SyncStatus

data class ShoppingUiState(
    val toBuy: List<ShoppingItem> = emptyList(),
    val bought: List<ShoppingItem> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.Syncing,
    /** True until the local database has emitted once, so empty states do not flash. */
    val isLoading: Boolean = true,
)
