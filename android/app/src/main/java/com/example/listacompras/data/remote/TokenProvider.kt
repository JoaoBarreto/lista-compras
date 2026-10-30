package com.example.listacompras.data.remote

/** Supplies the current auth token, or null when logged out. Implemented by the token store. */
fun interface TokenProvider {
    fun token(): String?
}
