package com.example.listacompras.data.remote

import com.example.listacompras.data.remote.dto.AuthResponse
import retrofit2.Call
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Blocking variant of `auth-refresh` for [TokenAuthenticator]. Synchronous calls bypass the OkHttp
 * dispatcher limit, so they cannot be starved by requests that are waiting for this very refresh.
 */
interface TokenRefreshApi {
    @POST("api/collections/users/auth-refresh")
    fun refresh(@Header("Authorization") token: String): Call<AuthResponse>
}
