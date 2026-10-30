package com.example.listacompras.data.sync

import com.example.listacompras.data.remote.PocketBaseApi
import com.example.listacompras.data.remote.dto.AuthResponse
import com.example.listacompras.data.remote.dto.ItemDto
import com.example.listacompras.data.remote.dto.ListDto
import com.example.listacompras.data.remote.dto.ListResponse
import com.example.listacompras.data.remote.dto.LoginRequest
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

    override suspend fun login(body: LoginRequest): AuthResponse = error("not used")
    override suspend fun refresh(): AuthResponse = error("not used")
    override suspend fun listLists(sort: String, perPage: Int): ListResponse<ListDto> = error("not used")
    override suspend fun listItems(filter: String, sort: String, perPage: Int, page: Int): ListResponse<ItemDto> =
        error("not used")
}
