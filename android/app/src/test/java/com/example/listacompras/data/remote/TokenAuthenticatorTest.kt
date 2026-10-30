package com.example.listacompras.data.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.listacompras.data.auth.FakeTokenCipher
import com.example.listacompras.data.auth.TokenStore
import com.example.listacompras.domain.model.AuthState
import com.example.listacompras.domain.model.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import java.util.Collections

class TokenAuthenticatorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val session = UserSession("user1", "Ana")
    private lateinit var tokenStore: TokenStore
    private lateinit var api: PocketBaseApi

    /** Authorization headers seen per path; the server only accepts [VALID] after a refresh. */
    private val seen = Collections.synchronizedList(mutableListOf<Pair<String, String?>>())
    @Volatile private var refreshCode = 200
    @Volatile private var refreshDisconnect = false

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val auth = request.headers["Authorization"]
                seen += path to auth
                return when {
                    path.endsWith("/auth-refresh") -> when {
                        refreshDisconnect -> MockResponse.Builder().code(503).build()
                        refreshCode == 200 -> json(AUTH_BODY)
                        else -> MockResponse.Builder().code(refreshCode).build()
                    }
                    auth == VALID -> json(LIST_BODY)
                    else -> MockResponse.Builder().code(401).build()
                }
            }
        }
        server.start()
        val file = tmp.newFile("auth.preferences_pb").also { it.delete() }
        tokenStore = TokenStore(PreferenceDataStoreFactory.create(scope = scope) { file }, FakeTokenCipher(), scope)
        runBlocking { tokenStore.save(EXPIRED, session) }

        val base = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokenStore)).build()
        val refreshApi = PocketBaseApiFactory.createRefreshApi(server.url("/").toString(), base)
        val client = base.newBuilder().authenticator(TokenAuthenticator(tokenStore, refreshApi)).build()
        api = PocketBaseApiFactory.create(server.url("/").toString(), client)
    }

    @After
    fun tearDown() {
        server.close()
        runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    private fun json(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()

    private fun refreshCalls() = seen.filter { it.first.endsWith("/auth-refresh") }

    @Test
    fun expiredTokenIsRefreshedAndRequestRetried() = runBlocking {
        val response = api.listItems("list=\"l\"")

        assertEquals(0, response.items.size)
        assertEquals(listOf("/api/collections/users/auth-refresh" to EXPIRED), refreshCalls())
        assertEquals(VALID, seen.last().second)
        assertEquals(VALID, tokenStore.token())
    }

    @Test
    fun rejectedRefreshLogsOut() = runBlocking {
        refreshCode = 401

        val error = runCatching { api.listItems("list=\"l\"") }.exceptionOrNull()

        assertTrue(error is HttpException && error.code() == 401)
        assertEquals(AuthState.LoggedOut, tokenStore.state.first { it is AuthState.LoggedOut })
    }

    @Test
    fun refreshServerErrorKeepsSession() = runBlocking {
        refreshDisconnect = true

        runCatching { api.listItems("list=\"l\"") }

        assertEquals(EXPIRED, tokenStore.token())
    }

    @Test
    fun unreachableServerDuringRefreshKeepsSession() = runBlocking {
        val dead = MockWebServer().apply { start() }
        val deadUrl = dead.url("/").toString()
        dead.close()
        val base = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokenStore)).build()
        val refreshApi = PocketBaseApiFactory.createRefreshApi(deadUrl, base)
        val client = base.newBuilder().authenticator(TokenAuthenticator(tokenStore, refreshApi)).build()
        val liveApi = PocketBaseApiFactory.create(server.url("/").toString(), client)

        runCatching { liveApi.listItems("list=\"l\"") }

        assertEquals(EXPIRED, tokenStore.token())
        assertTrue(tokenStore.state.value is AuthState.LoggedIn)
    }

    @Test
    fun concurrentUnauthorizedRequestsShareOneRefresh() = runBlocking {
        val results = (1..8).map { async(Dispatchers.IO) { api.listItems("list=\"l\"") } }.awaitAll()

        assertEquals(8, results.size)
        assertEquals(1, refreshCalls().size)
    }

    private companion object {
        const val EXPIRED = "expired-token"
        const val VALID = "fresh-token"
        const val AUTH_BODY = """{"token":"$VALID","record":{"id":"user1","email":"a@b.pt"}}"""
        const val LIST_BODY = """{"page":1,"perPage":200,"totalPages":1,"totalItems":0,"items":[]}"""
    }
}
