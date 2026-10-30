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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
        repository = AuthRepositoryImpl(api, tokenStore, Dispatchers.IO)
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
}
