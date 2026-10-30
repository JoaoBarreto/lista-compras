package com.example.listacompras.domain.repository

import com.example.listacompras.domain.model.Category
import com.example.listacompras.domain.model.ShoppingItem
import kotlinx.coroutines.flow.Flow

/** The UI's only view of the items: reads come from local storage, writes are queued for sync. */
interface ItemRepository {

    /** Items of the active list; empty while no list is known yet. */
    fun observeItems(): Flow<List<ShoppingItem>>

    /**
     * Adds an item. Returns false (and changes nothing) when there is no active list or no
     * logged-in user yet.
     */
    suspend fun add(name: String, quantity: String? = null, category: Category? = null): Boolean

    suspend fun setChecked(id: String, checked: Boolean)

    suspend fun delete(id: String)

    suspend fun clearChecked()
}
