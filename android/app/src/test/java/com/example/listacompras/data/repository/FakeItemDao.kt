package com.example.listacompras.data.repository

import com.example.listacompras.data.local.dao.ItemDao
import com.example.listacompras.data.local.entity.ItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeItemDao : ItemDao {
    val items = MutableStateFlow<Map<String, ItemEntity>>(emptyMap())
    override fun observeItems(listId: String): Flow<List<ItemEntity>> =
        items.map { m -> m.values.filter { it.listId == listId }.sortedBy { it.createdAt } }
    override suspend fun getItems(listId: String) = items.value.values.filter { it.listId == listId }
    override suspend fun getById(id: String) = items.value[id]
    override suspend fun upsert(item: ItemEntity) { items.value += item.id to item }
    override suspend fun upsertAll(items: List<ItemEntity>) = items.forEach { upsert(it) }
    override suspend fun deleteById(id: String) { items.value -= id }
    override suspend fun deleteByIds(ids: List<String>) { items.value -= ids.toSet() }
    override suspend fun getCheckedIds(listId: String) =
        items.value.values.filter { it.listId == listId && it.checked }.map { it.id }
    override suspend fun deleteChecked(listId: String) { deleteByIds(getCheckedIds(listId)) }
}
