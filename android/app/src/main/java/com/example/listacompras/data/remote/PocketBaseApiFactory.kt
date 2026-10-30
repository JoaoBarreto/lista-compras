package com.example.listacompras.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object PocketBaseApiFactory {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun create(baseUrl: String, client: OkHttpClient, json: Json = this.json): PocketBaseApi =
        retrofit(baseUrl, client, json).create(PocketBaseApi::class.java)

    fun createRefreshApi(baseUrl: String, client: OkHttpClient, json: Json = this.json): TokenRefreshApi =
        retrofit(baseUrl, client, json).create(TokenRefreshApi::class.java)

    private fun retrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
