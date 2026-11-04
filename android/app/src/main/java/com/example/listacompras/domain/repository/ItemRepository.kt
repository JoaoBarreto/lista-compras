package com.example.listacompras.domain.repository

import com.example.listacompras.domain.model.Category
import com.example.listacompras.domain.model.ListKind
import com.example.listacompras.domain.model.ShoppingItem
import kotlinx.coroutines.flow.Flow

/** The UI's only view of the items: reads come from local storage, writes are queued for sync. */
interface ItemRepository {

    /** Items of the list; empty while that list is not known yet. */
    fun observeItems(kind: ListKind): Flow<List<ShoppingItem>>

    /**
     * Adds an item. Returns false (and changes nothing) when the list is not known yet or there is
     * no logged-in user.
     */
    suspend fun add(kind: ListKind, name: String, quantity: String? = null, category: Category? = null): Boolean

    /**
     * Renames an item and sets its quantity. A blank [quantity] clears it; null leaves it untouched
     * (lists without quantities). Does nothing for an unknown id, a blank name or an unchanged item.
     */
    suspend fun update(id: String, name: String, quantity: String?)

    suspend fun setChecked(id: String, checked: Boolean)

    suspend fun delete(id: String)

    /** Deletes the checked items of one list. */
    suspend fun clearChecked(kind: ListKind)
}
