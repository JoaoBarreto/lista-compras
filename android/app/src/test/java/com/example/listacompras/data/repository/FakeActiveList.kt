package com.example.listacompras.data.repository

import kotlinx.coroutines.flow.MutableStateFlow

class FakeActiveList(initial: String?) : ActiveListProvider {
    val id = MutableStateFlow(initial)
    override fun observeListId() = id
    override suspend fun listId() = id.value
    override suspend fun setListId(id: String) { this.id.value = id }
}

class CountingSyncScheduler : com.example.listacompras.domain.repository.SyncScheduler {
    var requests = 0
        private set
    override fun requestSync() { requests++ }
}
