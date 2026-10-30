package com.example.listacompras.data.auth

import com.example.listacompras.data.remote.PocketBaseApi
import com.example.listacompras.data.remote.dto.LoginRequest
import com.example.listacompras.domain.model.AuthState
import com.example.listacompras.domain.model.UserSession
import com.example.listacompras.domain.repository.AuthRepository
import com.example.listacompras.domain.repository.LoginError
import com.example.listacompras.domain.repository.LoginResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.time.Clock
import java.time.Duration

class AuthRepositoryImpl(
    private val api: PocketBaseApi,
    private val tokenStore: TokenStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock = Clock.systemUTC(),
) : AuthRepository {

    override val authState: Flow<AuthState> = tokenStore.state

    override suspend fun login(email: String, password: String): LoginResult =
        withContext(ioDispatcher) {
            try {
                val response = api.login(LoginRequest(email.trim(), password))
                val session = UserSession(response.record.id, response.record.name)
                tokenStore.save(response.token, session)
                LoginResult.Success(session)
            } catch (e: HttpException) {
                // PocketBase answers wrong credentials with 400.
                LoginResult.Failure(
                    if (e.code() == 400 || e.code() == 401) LoginError.InvalidCredentials else LoginError.Unknown,
                )
            } catch (_: IOException) {
                LoginResult.Failure(LoginError.Network)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                LoginResult.Failure(LoginError.Unknown)
            }
        }

    override suspend fun refreshSessionIfNeeded() {
        // The stored session loads asynchronously; reading the token earlier would see "none".
        if (tokenStore.state.first { it !is AuthState.Loading } !is AuthState.LoggedIn) return
        val token = tokenStore.token() ?: return
        val expiresAt = JwtExpiry.expiresAt(token)
        if (expiresAt != null && expiresAt.isAfter(clock.instant().plus(REFRESH_THRESHOLD))) return
        withContext(ioDispatcher) {
            try {
                val response = api.refresh()
                val name = response.record.name ?: tokenStore.currentSession()?.displayName
                tokenStore.save(response.token, UserSession(response.record.id, name))
            } catch (e: HttpException) {
                if (e.code() in REJECTED && tokenStore.token() == token) tokenStore.clear()
            } catch (_: IOException) {
                // Offline: keep the session and retry on the next start.
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
            }
        }
    }

    override suspend fun logout() = tokenStore.clear()

    private companion object {
        /** Tokens last days; refreshing when under a week is left is cheap and keeps both phones logged in. */
        val REFRESH_THRESHOLD: Duration = Duration.ofDays(7)
        val REJECTED = setOf(400, 401, 403, 404)
    }
}
