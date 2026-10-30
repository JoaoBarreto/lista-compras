package com.example.listacompras.data.repository

import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.local.entity.SyncMetaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSyncMetaDao : SyncMetaDao {
    val values = MutableStateFlow<Map<String, String>>(emptyMap())
    override suspend fun get(key: String) = values.value[key]
    override fun observe(key: String): Flow<String?> = values.map { it[key] }
    override suspend fun put(meta: SyncMetaEntity) { values.value += meta.key to meta.value }
}
