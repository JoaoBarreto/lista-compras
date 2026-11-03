package com.example.listacompras.data.repository

/** Client-side copies of the server rules, so an op that the server would always reject is never queued. */
internal object CalendarLimits {
    const val TITLE_MAX = 100
    const val NOTES_MAX = 500
    const val TYPE_NAME_MAX = 30
    private val COLOR = Regex("^#[0-9A-Fa-f]{6}$")

    fun isColor(value: String) = COLOR.matches(value)
}
