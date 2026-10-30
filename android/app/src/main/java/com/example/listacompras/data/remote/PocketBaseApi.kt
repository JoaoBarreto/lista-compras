package com.example.listacompras.data.remote

import com.example.listacompras.data.remote.dto.AuthResponse
import com.example.listacompras.data.remote.dto.ItemDto
import com.example.listacompras.data.remote.dto.ListDto
import com.example.listacompras.data.remote.dto.ListResponse
import com.example.listacompras.data.remote.dto.LoginRequest
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PocketBaseApi {

    @POST("api/collections/users/auth-with-password")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/collections/users/auth-refresh")
    suspend fun refresh(): AuthResponse

    /** MVP: the app uses the oldest list, so callers ask for a single record sorted by creation. */
    @GET("api/collections/lists/records")
    suspend fun listLists(
        @Query("sort") sort: String = "created",
        @Query("perPage") perPage: Int = 1,
    ): ListResponse<ListDto>

    @GET("api/collections/items/records")
    suspend fun listItems(
        @Query("filter") filter: String,
        @Query("sort") sort: String = "created",
        @Query("perPage") perPage: Int = 200,
        @Query("page") page: Int = 1,
    ): ListResponse<ItemDto>

    @POST("api/collections/items/records")
    suspend fun createItem(@Body body: ItemDto): ItemDto

    /** [body] holds only the changed fields, e.g. `{"checked": true, "checked_by": "..."}`. */
    @PATCH("api/collections/items/records/{id}")
    suspend fun updateItem(@Path("id") id: String, @Body body: JsonObject): ItemDto

    /** Returns 204 on success. Callers treat 404 as success too. */
    @DELETE("api/collections/items/records/{id}")
    suspend fun deleteItem(@Path("id") id: String): Response<Unit>

    companion object {
        /** Filter selecting the items of one list, e.g. `list="abc123"`. */
        fun listFilter(listId: String): String = "list=\"$listId\""
    }
}
