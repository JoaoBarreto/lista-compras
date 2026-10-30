package com.example.listacompras.domain.model

data class UserSession(
    val userId: String,
    val displayName: String?,
)

sealed interface AuthState {
    /** Stored credentials have not been read yet. */
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val session: UserSession) : AuthState
}
