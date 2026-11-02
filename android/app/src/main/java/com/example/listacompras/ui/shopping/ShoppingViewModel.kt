package com.example.listacompras.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.listacompras.domain.model.SyncStatus
import com.example.listacompras.domain.model.ListKind
import com.example.listacompras.domain.repository.ItemRepository
import com.example.listacompras.domain.repository.ListRepository
import com.example.listacompras.domain.repository.SyncStatusProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Turns the local items and the sync status into one [ShoppingUiState]; UI events come in as functions. */
@HiltViewModel(assistedFactory = ShoppingViewModel.Factory::class)
class ShoppingViewModel @AssistedInject constructor(
    @Assisted private val kind: ListKind,
    private val itemRepository: ItemRepository,
    private val listRepository: ListRepository,
    private val syncStatusProvider: SyncStatusProvider,
) : ViewModel() {

    // The repository already orders items (creation time, then id); partitioning keeps that order.
    // Lazily (never stopped): the hidden tab keeps its state, and the scroll position that goes with it.
    val uiState: StateFlow<ShoppingUiState> = combine(
        itemRepository.observeItems(kind),
        // Items must not wait for the first status (it depends on network callbacks).
        syncStatusProvider.status.onStart { emit(SyncStatus.Syncing) },
        listRepository.hasList(kind),
    ) { items, status, hasList ->
        val (bought, toBuy) = items.partition { it.checked }
        ShoppingUiState(toBuy = toBuy, bought = bought, syncStatus = status, canAdd = hasList, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.Lazily, ShoppingUiState())

    /** Blank names are ignored, and so is anything while there is no list yet (the UI disables adding then). */
    /** [quantity] is optional: blank (or any value on a list without quantities) means none is stored. */
    fun onAdd(name: String, quantity: String = "") {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val qty = if (kind == ListKind.SHOPPING) quantity.trim().take(MAX_QUANTITY_LENGTH).ifEmpty { null } else null
        viewModelScope.launch {
            if (listRepository.hasList(kind).first()) itemRepository.add(kind, trimmed, qty)
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
        viewModelScope.launch { itemRepository.clearChecked(kind) }
    }

    @AssistedFactory
    interface Factory {
        fun create(kind: ListKind): ShoppingViewModel
    }
}
