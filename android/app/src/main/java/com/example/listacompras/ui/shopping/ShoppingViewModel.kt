package com.example.listacompras.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.listacompras.domain.model.SyncStatus
import com.example.listacompras.domain.model.ListKind
import com.example.listacompras.domain.repository.ItemRepository
import com.example.listacompras.domain.repository.ListRepository
import com.example.listacompras.domain.repository.SyncStatusProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Turns the local items and the sync status into one [ShoppingUiState]; UI events come in as functions. */
@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val listRepository: ListRepository,
    private val syncStatusProvider: SyncStatusProvider,
) : ViewModel() {

    // The repository already orders items (creation time, then id); partitioning keeps that order.
    val uiState: StateFlow<ShoppingUiState> = combine(
        itemRepository.observeItems(KIND),
        // Items must not wait for the first status (it depends on network callbacks).
        syncStatusProvider.status.onStart { emit(SyncStatus.Syncing) },
        listRepository.hasList(KIND),
    ) { items, status, hasList ->
        val (bought, toBuy) = items.partition { it.checked }
        ShoppingUiState(toBuy = toBuy, bought = bought, syncStatus = status, canAdd = hasList, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState())

    /** Blank names are ignored, and so is anything while there is no list yet (the UI disables adding then). */
    fun onAdd(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            if (listRepository.hasList(KIND).first()) itemRepository.add(KIND, trimmed)
        }
    }

    fun onCheckedChange(id: String, checked: Boolean) {
        viewModelScope.launch { itemRepository.setChecked(id, checked) }
    }

    fun onDelete(id: String) {
        viewModelScope.launch { itemRepository.delete(id) }
    }

    /** The user has seen the error banner: hide it until new operations get rejected. */
    fun onErrorAcknowledged() {
        viewModelScope.launch { syncStatusProvider.acknowledgeErrors() }
    }

    fun onClearChecked() {
        viewModelScope.launch { itemRepository.clearChecked(KIND) }
    }

    private companion object {
        // The tabs arrive with 8.3; until then the screen shows the shopping list only.
        val KIND = ListKind.SHOPPING
    }
}
