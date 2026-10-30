package com.example.listacompras.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val identity: String,
    val password: String,
)

@Serializable
data class AuthResponse(
    val token: String,
    val record: UserDto,
)

@Serializable
data class UserDto(
    val id: String,
    val email: String? = null,
    val name: String? = null,
)
