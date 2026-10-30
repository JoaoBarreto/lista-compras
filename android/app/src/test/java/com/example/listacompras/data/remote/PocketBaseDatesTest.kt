package com.example.listacompras.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PocketBaseDatesTest {

    @Test
    fun parsesPocketBaseFormat() {
        assertEquals(
            Instant.parse("2026-10-29T12:34:56.789Z"),
            PocketBaseDates.parse("2026-10-29 12:34:56.789Z"),
        )
    }

    @Test
    fun parsesIsoFormatToo() {
        assertEquals(
            Instant.parse("2026-10-29T12:34:56.789Z"),
            PocketBaseDates.parse("2026-10-29T12:34:56.789Z"),
        )
    }

    @Test
    fun emptyStringIsNull() {
        assertNull(PocketBaseDates.parse(""))
    }

    @Test
    fun formatRoundTrips() {
        val text = "2026-01-02 03:04:05.006Z"
        assertEquals(text, PocketBaseDates.format(PocketBaseDates.parse(text)!!))
    }
}
