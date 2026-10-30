package com.example.listacompras.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/** Adds the PocketBase `Authorization: <token>` header when a token is available. */
class AuthInterceptor(
    private val tokenProvider: TokenProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider.token()
        if (token.isNullOrBlank() || request.header(HEADER) != null) {
            return chain.proceed(request)
        }
        return chain.proceed(request.newBuilder().header(HEADER, token).build())
    }

    private companion object {
        const val HEADER = "Authorization"
    }
}
