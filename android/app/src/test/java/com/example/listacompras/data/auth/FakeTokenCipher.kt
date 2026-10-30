package com.example.listacompras.data.auth

/** Reversible fake that avoids the Android Keystore; [failDecrypt] simulates a lost key. */
class FakeTokenCipher : TokenCipher {
    var failDecrypt = false

    override fun encrypt(plain: String): String = "enc($plain)"

    override fun decrypt(encoded: String): String? =
        if (failDecrypt) null else encoded.removePrefix("enc(").removeSuffix(")")
}
