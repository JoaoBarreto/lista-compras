package com.example.listacompras.data.repository

import com.example.listacompras.data.local.dao.EventDao
import com.example.listacompras.data.local.dao.EventTypeDao
import com.example.listacompras.data.local.entity.EventEntity
import com.example.listacompras.data.local.entity.EventTypeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeEventTypeDao : EventTypeDao {
    val rows = MutableStateFlow<Map<String, EventTypeEntity>>(emptyMap())
    override fun observeAll(): Flow<List<EventTypeEntity>> = rows.map { it.values.toList() }
    override suspend fun getAll() = rows.value.values.toList()
    override suspend fun getById(id: String) = rows.value[id]
    override suspend fun upsert(type: EventTypeEntity) { rows.value += type.id to type }
    override suspend fun upsertAll(types: List<EventTypeEntity>) { rows.value += types.associateBy { it.id } }
    override suspend fun deleteById(id: String) { rows.value -= id }
    override suspend fun deleteByIds(ids: List<String>) { rows.value -= ids.toSet() }
}

class FakeEventDao : EventDao {
    val rows = MutableStateFlow<Map<String, EventEntity>>(emptyMap())
    override fun observeAll(): Flow<List<EventEntity>> = rows.map { it.values.toList() }
    override suspend fun getAll() = rows.value.values.toList()
    override suspend fun getById(id: String) = rows.value[id]
    override suspend fun upsert(event: EventEntity) { rows.value += event.id to event }
    override suspend fun upsertAll(events: List<EventEntity>) { rows.value += events.associateBy { it.id } }
    override suspend fun deleteById(id: String) { rows.value -= id }
    override suspend fun deleteByIds(ids: List<String>) { rows.value -= ids.toSet() }
}
