package com.example.listacompras.data.repository

import com.example.listacompras.data.remote.PocketBaseApi
import com.example.listacompras.domain.model.ListKind
import com.example.listacompras.domain.repository.ListRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class ListRepositoryImpl(
    private val api: PocketBaseApi,
    private val activeList: ActiveListProvider,
    private val ioDispatcher: CoroutineDispatcher,
) : ListRepository {

    override fun hasList(kind: ListKind): Flow<Boolean> =
        activeList.observeListId(kind).map { !it.isNullOrEmpty() }.distinctUntilChanged()

    override suspend fun ensureLists(): Boolean = withContext(ioDispatcher) {
        var shopping = activeList.listId(ListKind.SHOPPING)?.takeIf { it.isNotEmpty() }
        var tasks = activeList.listId(ListKind.TASKS)?.takeIf { it.isNotEmpty() }
        if (shopping != null && tasks != null) return@withContext true
        try {
            // Oldest first: the first list is shopping, the next one is tasks. Stored ids are never replaced.
            val lists = api.listLists(perPage = MAX_LISTS).items
            if (shopping == null) {
                shopping = lists.firstOrNull()?.id ?: return@withContext false
                activeList.setListId(ListKind.SHOPPING, shopping)
            }
            if (tasks == null) {
                tasks = lists.firstOrNull { it.id != shopping }?.id
                if (tasks != null) activeList.setListId(ListKind.TASKS, tasks)
            }
            true
        } catch (_: IOException) {
            shopping != null
        } catch (_: HttpException) {
            shopping != null
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            shopping != null
        }
    }

    private companion object {
        const val MAX_LISTS = 20
    }
}
