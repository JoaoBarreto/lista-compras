package com.example.listacompras.data.repository

import com.example.listacompras.data.remote.AuthInterceptor
import com.example.listacompras.data.remote.PocketBaseApiFactory
import com.example.listacompras.data.remote.TokenProvider
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
    private val activeList = object : ActiveListProvider {
        val id = MutableStateFlow<String?>(null)
        override fun observeListId() = id
        override suspend fun listId() = id.value
        override suspend fun setListId(id: String) { this.id.value = id }
    }
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
    fun hasActiveListFollowsTheStoredId() = runBlocking {
        assertFalse(repository.hasActiveList.first())
        activeList.id.value = "list1"
        assertTrue(repository.hasActiveList.first())
    }

    @Test
    fun fetchesTheFirstListAndStoresItsId() = runBlocking {
        server.enqueue(page("list1"))

        assertTrue(repository.ensureActiveList())

        assertEquals("list1", activeList.id.value)
        assertEquals("/api/collections/lists/records?sort=created&perPage=1", server.takeRequest().target)
    }

    @Test
    fun doesNotCallTheServerWhenTheListIsAlreadyKnown() = runBlocking {
        activeList.id.value = "known"

        assertTrue(repository.ensureActiveList())

        assertEquals(0, server.requestCount)
    }

    @Test
    fun returnsFalseAndStoresNothingWhenOffline() = runBlocking {
        server.close()

        assertFalse(repository.ensureActiveList())
        assertEquals(null, activeList.id.value)
    }

    @Test
    fun returnsFalseWhenThereAreNoLists() = runBlocking {
        server.enqueue(page())

        assertFalse(repository.ensureActiveList())
        assertEquals(null, activeList.id.value)
    }

    @Test
    fun returnsFalseOnServerError() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).build())

        assertFalse(repository.ensureActiveList())
    }
}
