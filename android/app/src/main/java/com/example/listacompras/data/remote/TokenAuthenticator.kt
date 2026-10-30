package com.example.listacompras.data.remote

import com.example.listacompras.data.auth.TokenStore
import com.example.listacompras.domain.model.UserSession
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException

/**
 * On 401, renews the token once (shared by concurrent requests) and retries the request.
 * A server-side rejection of the refresh clears the session, which sends the user to login;
 * network errors keep it, so being offline never logs the user out.
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val refreshApi: TokenRefreshApi,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        val failedToken = request.header(HEADER) ?: return null
        val path = request.url.encodedPath
        if (response.priorResponse != null || AUTH_PATHS.any { path.endsWith(it) }) return null

        synchronized(lock) {
            val current = tokenStore.token() ?: return null
            // Another request already renewed the token while this one waited for the lock.
            if (current != failedToken) return request.withToken(current)

            val result = try {
                refreshApi.refresh(current).execute()
            } catch (_: IOException) {
                return null
            }
            val body = result.body()
            return when {
                result.isSuccessful && body != null -> {
                    val name = body.record.name ?: tokenStore.currentSession()?.displayName
                    runBlocking { tokenStore.save(body.token, UserSession(body.record.id, name)) }
                    request.withToken(body.token)
                }
                result.code() in REJECTED -> {
                    // A login completed meanwhile must not be wiped.
                    if (tokenStore.token() == current) runBlocking { tokenStore.clear() }
                    null
                }
                else -> null
            }
        }
    }

    private fun Request.withToken(token: String): Request = newBuilder().header(HEADER, token).build()

    private companion object {
        const val HEADER = "Authorization"
        val AUTH_PATHS = listOf("/auth-with-password", "/auth-refresh")
        val REJECTED = setOf(400, 401, 403, 404)
    }
}
