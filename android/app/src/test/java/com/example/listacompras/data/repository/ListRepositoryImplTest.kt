package com.example.listacompras.data.repository

import com.example.listacompras.data.remote.AuthInterceptor
import com.example.listacompras.data.remote.PocketBaseApiFactory
import com.example.listacompras.data.remote.TokenProvider
import com.example.listacompras.domain.model.ListKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ListRepositoryImplTest {

    private val server = MockWebServer()
    private val activeList = FakeActiveList(null)
    private lateinit var repository: ListRepositoryImpl

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(TokenProvider { "t" })).build()
        val api = PocketBaseApiFactory.create(server.url("/").toString(), client)
        repository = ListRepositoryImpl(api, activeList, Dispatchers.IO)
    }

    @After
    fun tearDown() = server.close()

    private fun page(vararg ids: String) = MockResponse.Builder().body(
        """{"page":1,"perPage":1,"totalPages":1,"totalItems":${ids.size},"items":[""" +
            ids.joinToString(",") { """{"id":"$it","name":"Compras"}""" } + "]}",
    ).build()

    @Test
    fun hasListFollowsTheStoredId() = runBlocking {
        assertFalse(repository.hasList(ListKind.SHOPPING).first())
        activeList.id.value = "list1"
        assertTrue(repository.hasList(ListKind.SHOPPING).first())
    }

    @Test
    fun fetchesTheFirstListAndStoresItsId() = runBlocking {
        server.enqueue(page("list1"))

        assertTrue(repository.ensureLists())

        assertEquals("list1", activeList.id.value)
        assertEquals("/api/collections/lists/records?sort=created&perPage=20", server.takeRequest().target)
    }

    @Test
    fun storesTheSecondListAsTasks() = runBlocking {
        server.enqueue(page("list1", "list2"))

        assertTrue(repository.ensureLists())

        assertEquals("list1", activeList.id.value)
        assertEquals("list2", activeList.tasksId.value)
    }

    @Test
    fun doesNotCallTheServerWhenBothListsAreKnown() = runBlocking {
        activeList.id.value = "known"
        activeList.tasksId.value = "tasks"

        assertTrue(repository.ensureLists())

        assertEquals(0, server.requestCount)
    }

    @Test
    fun anInstallWithOnlyShoppingGetsTasksWithoutChangingShopping() = runBlocking {
        activeList.id.value = "shop"
        server.enqueue(page("shop", "tasks"))

        assertTrue(repository.ensureLists())

        assertEquals("shop", activeList.id.value)
        assertEquals("tasks", activeList.tasksId.value)
    }

    @Test
    fun theTasksListIsTheOldestOneThatIsNotShopping() = runBlocking {
        activeList.id.value = "second"
        server.enqueue(page("first", "second", "third"))

        assertTrue(repository.ensureLists())

        assertEquals("second", activeList.id.value)
        assertEquals("first", activeList.tasksId.value)
    }

    @Test
    fun aServerWithOnlyOneListStillGivesShopping() = runBlocking {
        server.enqueue(page("list1"))

        assertTrue(repository.ensureLists())

        assertEquals("list1", activeList.id.value)
        assertEquals(null, activeList.tasksId.value)
    }

    @Test
    fun withShoppingKnownAnOfflineTasksLookupIsStillTrue() = runBlocking {
        activeList.id.value = "shop"
        server.close()

        assertTrue(repository.ensureLists())
        assertEquals(null, activeList.tasksId.value)
    }

    @Test
    fun returnsFalseAndStoresNothingWhenOffline() = runBlocking {
        server.close()

        assertFalse(repository.ensureLists())
        assertEquals(null, activeList.id.value)
    }

    @Test
    fun returnsFalseWhenThereAreNoLists() = runBlocking {
        server.enqueue(page())

        assertFalse(repository.ensureLists())
        assertEquals(null, activeList.id.value)
    }

    @Test
    fun returnsFalseOnServerError() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).build())

        assertFalse(repository.ensureLists())
    }
}
