package com.example.listacompras.domain.repository

import com.example.listacompras.domain.model.AuthState
import com.example.listacompras.domain.model.UserSession
import kotlinx.coroutines.flow.Flow

enum class LoginError { InvalidCredentials, Network, Unknown }

sealed interface LoginResult {
    data class Success(val session: UserSession) : LoginResult
    data class Failure(val error: LoginError) : LoginResult
}

interface AuthRepository {
    val authState: Flow<AuthState>

    suspend fun login(email: String, password: String): LoginResult

    /** Clears the stored credentials only; local data is kept. */
    suspend fun logout()
}
