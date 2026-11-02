package com.example.listacompras.data.repository

import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.local.entity.SyncMetaEntity
import com.example.listacompras.domain.model.ListKind
import kotlinx.coroutines.flow.Flow

/** Server ids of the app's lists, stored locally. Null/empty until each one is known. */
interface ActiveListProvider {
    fun observeListId(kind: ListKind): Flow<String?>
    suspend fun listId(kind: ListKind): String?
    suspend fun setListId(kind: ListKind, id: String)

    /** Ids of every list that is known, for pulling and for accepting realtime events. */
    suspend fun knownListIds(): Set<String>
}

class SyncMetaActiveListProvider(private val dao: SyncMetaDao) : ActiveListProvider {
    override fun observeListId(kind: ListKind): Flow<String?> = dao.observe(key(kind))
    override suspend fun listId(kind: ListKind): String? = dao.get(key(kind))
    override suspend fun setListId(kind: ListKind, id: String) = dao.put(SyncMetaEntity(key(kind), id))

    override suspend fun knownListIds(): Set<String> =
        ListKind.entries.mapNotNull { listId(it)?.takeIf(String::isNotEmpty) }.toSet()

    private fun key(kind: ListKind) = when (kind) {
        // Kept as it was before there were two lists, so existing installs keep their shopping list.
        ListKind.SHOPPING -> "activeListId"
        ListKind.TASKS -> "tasksListId"
    }
}
