package com.example.listacompras.data.auth

/** Reversible encryption for secrets at rest. Kept as an interface so tests can avoid the Keystore. */
interface TokenCipher {
    fun encrypt(plain: String): String

    /** Returns null when [encoded] cannot be decrypted (e.g. the key was lost). Never throws. */
    fun decrypt(encoded: String): String?
}
