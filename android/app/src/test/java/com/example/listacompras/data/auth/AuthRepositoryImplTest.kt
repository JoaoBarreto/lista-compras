package com.example.listacompras.data.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.listacompras.data.remote.AuthInterceptor
import com.example.listacompras.data.remote.PocketBaseApiFactory
import com.example.listacompras.domain.model.AuthState
import com.example.listacompras.domain.model.UserSession
import com.example.listacompras.domain.repository.LoginError
import com.example.listacompras.domain.repository.LoginResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration
import java.time.Instant

class AuthRepositoryImplTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val scope = CoroutineScope(Dispatchers.Default)
    private lateinit var tokenStore: TokenStore
    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setUp() {
        server.start()
        val file = tmp.newFile("auth.preferences_pb").also { it.delete() }
        tokenStore = TokenStore(PreferenceDataStoreFactory.create(scope = scope) { file }, FakeTokenCipher(), scope)
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokenStore)).build()
        val api = PocketBaseApiFactory.create(server.url("/").toString(), client)
        repository = AuthRepositoryImpl(api, tokenStore, Dispatchers.IO, loginTimeout = Duration.ofMillis(300))
    }

    @After
    fun tearDown() {
        server.close()
        scope.cancel()
    }

    @Test
    fun successfulLoginStoresTokenAndSession() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "application/json")
                .body("""{"token":"tok123","record":{"id":"user1","email":"a@b.pt","name":"Ana"}}""")
                .build(),
        )

        val result = repository.login(" a@b.pt ", "secret")

        val session = UserSession("user1", "Ana")
        assertEquals(LoginResult.Success(session), result)
        assertEquals(AuthState.LoggedIn(session), tokenStore.state.first { it is AuthState.LoggedIn })
        assertEquals("tok123", tokenStore.token())
        assertEquals("""{"identity":"a@b.pt","password":"secret"}""", server.takeRequest().body?.utf8())
    }

    @Test
    fun wrongCredentialsReturnInvalidCredentials() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .addHeader("Content-Type", "application/json")
                .body("""{"data":{},"message":"Failed to authenticate.","status":400}""")
                .build(),
        )

        assertEquals(LoginResult.Failure(LoginError.InvalidCredentials), repository.login("a@b.pt", "bad"))
        assertNull(tokenStore.token())
    }

    @Test
    fun serverErrorReturnsUnknown() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).build())

        assertEquals(LoginResult.Failure(LoginError.Unknown), repository.login("a@b.pt", "x"))
    }

    @Test
    fun aServerThatNeverAnswersEndsInNetworkErrorAfterTheTimeout() = runBlocking {
        server.enqueue(MockResponse.Builder().headersDelay(10, java.util.concurrent.TimeUnit.SECONDS).build())

        assertEquals(LoginResult.Failure(LoginError.Network), repository.login("a@b.pt", "x"))
    }

    @Test
    fun unreachableServerReturnsNetworkError() = runBlocking {
        server.close()

        assertEquals(LoginResult.Failure(LoginError.Network), repository.login("a@b.pt", "x"))
    }

    @Test
    fun logoutClearsToken() = runBlocking {
        tokenStore.save("tok", UserSession("u", null))
        tokenStore.state.first { it is AuthState.LoggedIn }

        repository.logout()

        assertEquals(AuthState.LoggedOut, tokenStore.state.first { it is AuthState.LoggedOut })
    }

    private fun jwt(expiresAt: Instant): String {
        val enc = java.util.Base64.getUrlEncoder().withoutPadding()
        val payload = enc.encodeToString("""{"exp":${expiresAt.epochSecond}}""".toByteArray())
        return "${enc.encodeToString("{}".toByteArray())}.$payload.sig"
    }

    private suspend fun loginWith(token: String) {
        tokenStore.save(token, UserSession("user1", "Ana"))
        tokenStore.state.first { it is AuthState.LoggedIn }
    }

    @Test
    fun tokenNearExpiryIsRefreshedAtStart() = runBlocking {
        val old = jwt(Instant.now().plus(Duration.ofDays(2)))
        loginWith(old)
        server.enqueue(
            MockResponse.Builder().addHeader("Content-Type", "application/json")
                .body("""{"token":"newtok","record":{"id":"user1"}}""").build(),
        )

        repository.refreshSessionIfNeeded()

        assertEquals(old, server.takeRequest().headers["Authorization"])
        assertEquals("newtok", tokenStore.token())
        assertEquals(AuthState.LoggedIn(UserSession("user1", "Ana")), tokenStore.state.value)
    }

    @Test
    fun freshTokenIsNotRefreshed() = runBlocking {
        loginWith(jwt(Instant.now().plus(Duration.ofDays(13))))

        repository.refreshSessionIfNeeded()

        assertEquals(0, server.requestCount)
    }

    @Test
    fun refreshRejectedByServerLogsOut() = runBlocking {
        loginWith(jwt(Instant.now().minus(Duration.ofDays(1))))
        server.enqueue(MockResponse.Builder().code(401).build())

        repository.refreshSessionIfNeeded()

        assertEquals(AuthState.LoggedOut, tokenStore.state.first { it is AuthState.LoggedOut })
    }

    @Test
    fun refreshOfflineKeepsSession() = runBlocking {
        val old = jwt(Instant.now().plus(Duration.ofDays(1)))
        loginWith(old)
        server.close()

        repository.refreshSessionIfNeeded()

        assertEquals(old, tokenStore.token())
    }

    /** Persists [token] with a store that is then closed, and returns a repository on a freshly opened store. */
    private suspend fun coldStartRepository(token: String): AuthRepositoryImpl {
        val file = tmp.newFile("cold.preferences_pb").also { it.delete() }
        val firstScope = CoroutineScope(Dispatchers.Default + Job())
        TokenStore(PreferenceDataStoreFactory.create(scope = firstScope) { file }, FakeTokenCipher(), firstScope)
            .save(token, UserSession("user1", "Ana"))
        firstScope.coroutineContext[Job]!!.cancelAndJoin()

        val store = TokenStore(PreferenceDataStoreFactory.create(scope = scope) { file }, FakeTokenCipher(), scope)
        coldStore = store
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(store)).build()
        val api = PocketBaseApiFactory.create(server.url("/").toString(), client)
        return AuthRepositoryImpl(api, store, Dispatchers.IO)
    }

    private lateinit var coldStore: TokenStore

    @Test
    fun coldStartWithFreshTokenDoesNotRefreshOrLogOut() = runBlocking {
        val repo = coldStartRepository(jwt(Instant.now().plus(Duration.ofDays(13))))

        repo.refreshSessionIfNeeded()

        assertEquals(0, server.requestCount)
        assertTrue(coldStore.state.value is AuthState.LoggedIn)
    }

    @Test
    fun coldStartWithNearExpiryTokenRefreshesWithStoredToken() = runBlocking {
        val old = jwt(Instant.now().plus(Duration.ofDays(2)))
        val repo = coldStartRepository(old)
        server.enqueue(
            MockResponse.Builder().addHeader("Content-Type", "application/json")
                .body("""{"token":"newtok","record":{"id":"user1"}}""").build(),
        )

        repo.refreshSessionIfNeeded()

        assertEquals(old, server.takeRequest().headers["Authorization"])
        assertEquals("newtok", coldStore.token())
    }
}
