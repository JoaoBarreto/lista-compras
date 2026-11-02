package com.example.listacompras.data.repository

import com.example.listacompras.domain.model.ListKind
import kotlinx.coroutines.flow.MutableStateFlow

class FakeActiveList(initial: String?, tasksInitial: String? = null) : ActiveListProvider {
    val id = MutableStateFlow(initial)
    val tasksId = MutableStateFlow(tasksInitial)
    private fun flow(kind: ListKind) = if (kind == ListKind.SHOPPING) id else tasksId
    override fun observeListId(kind: ListKind) = flow(kind)
    override suspend fun listId(kind: ListKind) = flow(kind).value
    override suspend fun setListId(kind: ListKind, id: String) { flow(kind).value = id }
    override suspend fun knownListIds() = setOfNotNull(id.value, tasksId.value).filter { it.isNotEmpty() }.toSet()
}

class CountingSyncScheduler : com.example.listacompras.domain.repository.SyncScheduler {
    var requests = 0
        private set
    override fun requestSync() { requests++ }
}
