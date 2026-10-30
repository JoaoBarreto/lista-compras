package com.example.listacompras.data.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.util.Base64

/** Reads the `exp` claim of a JWT without verifying it (only used to schedule a refresh). */
object JwtExpiry {

    fun expiresAt(token: String): Instant? = try {
        val payload = Base64.getUrlDecoder().decode(token.split('.')[1])
        val exp = (Json.parseToJsonElement(String(payload, Charsets.UTF_8)) as JsonObject)["exp"]
        exp?.jsonPrimitive?.longOrNull?.let(Instant::ofEpochSecond)
    } catch (_: Exception) {
        null
    }
}
