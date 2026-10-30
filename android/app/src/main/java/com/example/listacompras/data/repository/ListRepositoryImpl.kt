package com.example.listacompras.data.repository

import com.example.listacompras.data.remote.PocketBaseApi
import com.example.listacompras.domain.repository.ListRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class ListRepositoryImpl(
    private val api: PocketBaseApi,
    private val activeList: ActiveListProvider,
    private val ioDispatcher: CoroutineDispatcher,
) : ListRepository {

    override suspend fun ensureActiveList(): Boolean = withContext(ioDispatcher) {
        if (!activeList.listId().isNullOrEmpty()) return@withContext true
        try {
            val list = api.listLists().items.firstOrNull() ?: return@withContext false
            activeList.setListId(list.id)
            true
        } catch (_: IOException) {
            false
        } catch (_: HttpException) {
            false
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            false
        }
    }
}
