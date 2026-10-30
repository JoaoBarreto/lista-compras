package com.example.listacompras.data.remote

import com.example.listacompras.data.remote.dto.ItemDto
import com.example.listacompras.data.remote.dto.LoginRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.time.Instant

class PocketBaseApiTest {

    private val server = MockWebServer()
    private var token: String? = null
    private lateinit var api: PocketBaseApi

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { token })
            .build()
        api = PocketBaseApiFactory.create(server.url("/").toString(), client)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun loginPostsCredentialsAndParsesToken() = runTest {
        enqueueJson(
            """{"token":"tok123","record":{"id":"user1","email":"a@b.pt","name":"Ana","verified":true}}""",
        )

        val response = api.login(LoginRequest("a@b.pt", "secret"))

        assertEquals("tok123", response.token)
        assertEquals("user1", response.record.id)
        assertEquals("Ana", response.record.name)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/collections/users/auth-with-password", request.url.encodedPath)
        assertNull(request.headers["Authorization"])
        val body = request.jsonBody()
        assertEquals("a@b.pt", body["identity"]!!.jsonPrimitive.content)
        assertEquals("secret", body["password"]!!.jsonPrimitive.content)
    }

    @Test
    fun authenticatedRequestsCarryToken() = runTest {
        token = "tok123"
        enqueueJson("""{"token":"tok456","record":{"id":"user1"}}""")

        val response = api.refresh()

        assertEquals("tok456", response.token)
        val request = server.takeRequest()
        assertEquals("/api/collections/users/auth-refresh", request.url.encodedPath)
        assertEquals("tok123", request.headers["Authorization"])
    }

    @Test
    fun listItemsSendsQueryAndParsesPage() = runTest {
        token = "tok"
        enqueueJson(
            """
            {
              "page": 1, "perPage": 200, "totalPages": 1, "totalItems": 2,
              "items": [
                {
                  "collectionId": "pbc_1", "collectionName": "items",
                  "id": "aaaaaaaaaaaaaaa", "list": "list1", "name": "Leite", "quantity": "2",
                  "checked": false, "category": "", "added_by": "user1", "checked_by": "",
                  "created": "2026-10-29 12:34:56.789Z", "updated": "2026-10-29 12:35:00.000Z"
                },
                {
                  "id": "bbbbbbbbbbbbbbb", "list": "list1", "name": "Pão", "quantity": "",
                  "checked": true, "category": "pantry", "added_by": "user1", "checked_by": "user2",
                  "created": "2026-10-29 13:00:00.000Z", "updated": "2026-10-29 13:05:00.000Z"
                }
              ]
            }
            """.trimIndent(),
        )

        val response = api.listItems(filter = PocketBaseApi.listFilter("list1"))

        assertEquals(1, response.totalPages)
        assertEquals(2, response.items.size)
        val first = response.items[0]
        assertEquals("Leite", first.name)
        assertEquals("user1", first.addedBy)
        assertEquals(Instant.parse("2026-10-29T12:34:56.789Z"), first.created)
        assertEquals("user2", response.items[1].checkedBy)

        val url = server.takeRequest().url
        assertEquals("/api/collections/items/records", url.encodedPath)
        assertEquals("list=\"list1\"", url.queryParameter("filter"))
        assertEquals("created", url.queryParameter("sort"))
        assertEquals("200", url.queryParameter("perPage"))
        assertEquals("1", url.queryParameter("page"))
    }

    @Test
    fun createItemSendsClientIdAndOmitsUnsetFields() = runTest {
        token = "tok"
        enqueueJson(
            """
            {"id":"a1b2c3d4e5f6g7h","list":"list1","name":"Leite","quantity":"2","checked":false,
             "category":"","added_by":"user1","checked_by":"",
             "created":"2026-10-29 12:34:56.789Z","updated":"2026-10-29 12:34:56.789Z"}
            """.trimIndent(),
        )
        val item = ItemDto(
            id = "a1b2c3d4e5f6g7h",
            list = "list1",
            name = "Leite",
            quantity = "2",
            checked = false,
            addedBy = "user1",
        )

        val created = api.createItem(item)

        assertEquals("a1b2c3d4e5f6g7h", created.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/collections/items/records", request.url.encodedPath)
        val body = request.jsonBody()
        assertEquals("a1b2c3d4e5f6g7h", body["id"]!!.jsonPrimitive.content)
        assertEquals("user1", body["added_by"]!!.jsonPrimitive.content)
        assertFalse(body["checked"]!!.jsonPrimitive.boolean)
        assertFalse("checked_by" in body)
        assertFalse("category" in body)
        assertFalse("created" in body)
        assertFalse("updated" in body)
    }

    @Test
    fun updateItemPatchesOnlyChangedFields() = runTest {
        token = "tok"
        enqueueJson(
            """
            {"id":"a1b2c3d4e5f6g7h","list":"list1","name":"Leite","checked":true,
             "added_by":"user1","checked_by":"user2",
             "created":"2026-10-29 12:34:56.789Z","updated":"2026-10-29 12:40:00.000Z"}
            """.trimIndent(),
        )

        val updated = api.updateItem(
            "a1b2c3d4e5f6g7h",
            buildJsonObject {
                put("checked", true)
                put("checked_by", "user2")
            },
        )

        assertTrue(updated.checked)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/collections/items/records/a1b2c3d4e5f6g7h", request.url.encodedPath)
        assertEquals(setOf("checked", "checked_by"), request.jsonBody().keys)
    }

    @Test
    fun deleteItemReturns204() = runTest {
        token = "tok"
        server.enqueue(MockResponse.Builder().code(204).build())

        val response = api.deleteItem("a1b2c3d4e5f6g7h")

        assertEquals(204, response.code())
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/collections/items/records/a1b2c3d4e5f6g7h", request.url.encodedPath)
    }

    @Test
    fun deleteItemExposes404WithoutThrowing() = runTest {
        token = "tok"
        enqueueJson("""{"status":404,"message":"Not found.","data":{}}""", code = 404)

        val response = api.deleteItem("missing00000000")

        assertEquals(404, response.code())
    }

    @Test(expected = HttpException::class)
    fun errorStatusThrowsHttpException() = runTest {
        enqueueJson("""{"status":400,"message":"Failed to authenticate.","data":{}}""", code = 400)

        api.login(LoginRequest("a@b.pt", "wrong"))
    }

    private fun enqueueJson(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .addHeader("Content-Type", "application/json")
                .body(body)
                .build(),
        )
    }

    private fun RecordedRequest.jsonBody(): JsonObject =
        Json.parseToJsonElement(body!!.utf8()).jsonObject
}
