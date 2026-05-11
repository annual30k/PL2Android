package com.patrollink.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.patrollink.domain.AuthSession
import com.patrollink.domain.SecureStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreSecureStore(context: Context) : SecureStore {
    private val prefs = context.getSharedPreferences("patrol_secure_session", Context.MODE_PRIVATE)
    private val keyAlias = "patrol_session_aes"
    private val androidKeyStore = "AndroidKeyStore"

    override suspend fun saveSession(session: AuthSession) {
        prefs.edit()
            .putString("access", encrypt(session.accessToken))
            .putString("refresh", encrypt(session.refreshToken))
            .putLong("expires", session.expiresInSeconds)
            .apply()
    }

    override suspend fun readSession(): AuthSession? {
        val access = prefs.getString("access", null)?.let(::decrypt) ?: return null
        val refresh = prefs.getString("refresh", null)?.let(::decrypt) ?: return null
        val expires = prefs.getLong("expires", 0)
        return AuthSession(access, refresh, expires)
    }

    override suspend fun clearSession() {
        prefs.edit().clear().apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.encodeToByteArray())
        return "${iv.base64()}:${encrypted.base64()}"
    }

    private fun decrypt(encoded: String): String {
        val (ivEncoded, encryptedEncoded) = encoded.split(":", limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, ivEncoded.fromBase64()))
        return cipher.doFinal(encryptedEncoded.fromBase64()).decodeToString()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(androidKeyStore).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, androidKeyStore)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun ByteArray.base64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
}
