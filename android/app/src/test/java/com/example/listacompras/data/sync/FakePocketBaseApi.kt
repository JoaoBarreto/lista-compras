package com.example.listacompras.data.sync

import com.example.listacompras.data.remote.PocketBaseApi
import com.example.listacompras.data.remote.dto.AuthResponse
import com.example.listacompras.data.remote.dto.ItemDto
import com.example.listacompras.data.remote.dto.ListDto
import com.example.listacompras.data.remote.dto.ListResponse
import com.example.listacompras.data.remote.dto.LoginRequest
import com.example.listacompras.data.remote.dto.SubscribeRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * In-memory PocketBase `items` collection. Failures are queued per call: each queued entry is
 * consumed by the next call and decides whether the request is applied before/after failing.
 */
class FakePocketBaseApi : PocketBaseApi {

    sealed interface Failure {
        /** The request never reaches the server. */
        data object NetworkDown : Failure

        /** The server applies the request but the response is lost. */
        data object ResponseLost : Failure
        data class Http(val code: Int) : Failure
    }

    val server = linkedMapOf<String, ItemDto>()
    val failures = ArrayDeque<Failure>()
    var calls = 0
        private set

    private inline fun <T> handle(apply: () -> T): T {
        calls++
        when (val failure = failures.removeFirstOrNull()) {
            null -> return apply()
            Failure.NetworkDown -> throw IOException("down")
            Failure.ResponseLost -> {
                apply()
                throw IOException("response lost")
            }
            is Failure.Http -> throw http(failure.code)
        }
    }

    private fun http(code: Int) = HttpException(Response.error<Unit>(code, "".toResponseBody()))

    override suspend fun createItem(body: ItemDto): ItemDto = handle {
        // PocketBase rejects an existing id with 400.
        if (body.id in server) throw http(400)
        server[body.id] = body
        body
    }

    override suspend fun getItem(id: String): ItemDto = handle { server[id] ?: throw http(404) }

    override suspend fun updateItem(id: String, body: JsonObject): ItemDto = handle {
        val current = server[id] ?: throw http(404)
        val updated = current.copy(
            checked = (body["checked"] as? JsonPrimitive)?.booleanOrNull ?: current.checked,
            checkedBy = if ("checked_by" in body) (body["checked_by"] as JsonPrimitive).content else current.checkedBy,
        )
        server[id] = updated
        updated
    }

    override suspend fun deleteItem(id: String): Response<Unit> {
        calls++
        return when (val failure = failures.removeFirstOrNull()) {
            null -> deleteNow(id)
            Failure.NetworkDown -> throw IOException("down")
            Failure.ResponseLost -> { deleteNow(id); throw IOException("response lost") }
            is Failure.Http -> Response.error(failure.code, "".toResponseBody())
        }
    }

    private fun deleteNow(id: String): Response<Unit> =
        if (server.remove(id) != null) Response.success(Unit) else Response.error(404, "".toResponseBody())

    /** Generic collections (`events`, `event_types`), by collection name then id. */
    val records = mutableMapOf<String, LinkedHashMap<String, JsonObject>>()

    /** Collections whose list call fails with this HTTP code (to simulate missing/denied ones). */
    val listFailures = mutableMapOf<String, Int>()

    private fun table(collection: String) = records.getOrPut(collection) { linkedMapOf() }

    override suspend fun listRecords(collection: String, sort: String, perPage: Int, page: Int): ListResponse<JsonObject> {
        listFailures[collection]?.let { calls++; throw http(it) }
        return handle {
            val all = table(collection).values.toList()
            val size = minOf(perPage, maxPerPage)
            val totalPages = maxOf(1, (all.size + size - 1) / size)
            ListResponse(page, size, totalPages, all.size, all.drop((page - 1) * size).take(size))
        }
    }

    override suspend fun getRecord(collection: String, id: String): JsonObject =
        handle { table(collection)[id] ?: throw http(404) }

    override suspend fun createRecord(collection: String, body: JsonObject): JsonObject = handle {
        val id = (body["id"] as JsonPrimitive).content
        if (id in table(collection)) throw http(400)
        table(collection)[id] = body
        body
    }

    override suspend fun updateRecord(collection: String, id: String, body: JsonObject): JsonObject = handle {
        val current = table(collection)[id] ?: throw http(404)
        JsonObject(current + body).also { table(collection)[id] = it }
    }

    override suspend fun deleteRecord(collection: String, id: String): Response<Unit> {
        calls++
        return when (val failure = failures.removeFirstOrNull()) {
            null -> if (table(collection).remove(id) != null) Response.success(Unit) else Response.error(404, "".toResponseBody())
            Failure.NetworkDown -> throw IOException("down")
            Failure.ResponseLost -> { table(collection).remove(id); throw IOException("response lost") }
            is Failure.Http -> Response.error(failure.code, "".toResponseBody())
        }
    }

    override suspend fun subscribe(body: SubscribeRequest): Response<Unit> = error("not used")
    override suspend fun login(body: LoginRequest): AuthResponse = error("not used")
    override suspend fun refresh(): AuthResponse = error("not used")
    override suspend fun listLists(sort: String, perPage: Int): ListResponse<ListDto> = error("not used")
    /** Caps `perPage` so tests can force several pages. */
    var maxPerPage = Int.MAX_VALUE

    /** Runs after a page is computed, i.e. while the client is still fetching (to simulate races). */
    var onListItems: (suspend (page: Int) -> Unit)? = null

    override suspend fun listItems(filter: String, sort: String, perPage: Int, page: Int): ListResponse<ItemDto> {
        val listId = Regex("list=\"([^\"]*)\"").matchEntire(filter)!!.groupValues[1]
        val response = handle {
            val all = server.values.filter { it.list == listId }
            val size = minOf(perPage, maxPerPage)
            val totalPages = maxOf(1, (all.size + size - 1) / size)
            ListResponse(page, size, totalPages, all.size, all.drop((page - 1) * size).take(size))
        }
        onListItems?.invoke(page)
        return response
    }
}
