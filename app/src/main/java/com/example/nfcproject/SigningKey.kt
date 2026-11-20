package com.example.nfcproject

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

object SigningKey {
    private const val ALIAS = "patrol_hmac"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let{ return it }

        val gen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore"
        )
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return gen.generateKey()
    }

    fun sign(data: String, uid: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key())
        mac.update(uid)
        mac.update(data.toByteArray())
        return mac.doFinal().take(8).joinToString ("") { "%02x".format(it) }
    }

    fun verify(data: String, uid: ByteArray, signature: String): Boolean =
        MessageDigest.isEqual(sign(data, uid).toByteArray(),signature.toByteArray())
}