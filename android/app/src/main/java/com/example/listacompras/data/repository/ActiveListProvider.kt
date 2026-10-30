package com.example.listacompras.data.repository

import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.local.entity.SyncMetaEntity
import kotlinx.coroutines.flow.Flow

/** The list the app works on (MVP: the first list on the server). Null until it is known. */
interface ActiveListProvider {
    fun observeListId(): Flow<String?>
    suspend fun listId(): String?
    suspend fun setListId(id: String)
}

class SyncMetaActiveListProvider(private val dao: SyncMetaDao) : ActiveListProvider {
    override fun observeListId(): Flow<String?> = dao.observe(KEY)
    override suspend fun listId(): String? = dao.get(KEY)
    override suspend fun setListId(id: String) = dao.put(SyncMetaEntity(KEY, id))

    private companion object {
        const val KEY = "activeListId"
    }
}
