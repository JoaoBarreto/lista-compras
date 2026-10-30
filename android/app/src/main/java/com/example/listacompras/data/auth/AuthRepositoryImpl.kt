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
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class AuthRepositoryImpl(
    private val api: PocketBaseApi,
    private val tokenStore: TokenStore,
    private val ioDispatcher: CoroutineDispatcher,
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

    override suspend fun logout() = tokenStore.clear()
}
