package com.example.listacompras.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * PocketBase dates look like `2026-10-29 12:34:56.789Z`: ISO-8601 in UTC, but with a space
 * instead of `T` between date and time.
 */
object PocketBaseDates {

    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    /** Parses a PocketBase date. Returns null for the empty string PocketBase uses for unset dates. */
    fun parse(value: String): Instant? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        return Instant.parse(trimmed.replaceFirst(' ', 'T'))
    }

    fun format(instant: Instant): String = formatter.format(instant)
}

object PocketBaseInstantSerializer : KSerializer<Instant?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PocketBaseInstant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant? = PocketBaseDates.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Instant?) {
        encoder.encodeString(value?.let(PocketBaseDates::format).orEmpty())
    }
}
