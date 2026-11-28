
package com.frankdev.xxxcorevpn

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object ClientSessionStore {

    private const val KEY_ALIAS = "XXX_CORE_VPN_SESSION_KEY"
    private const val PREFS_NAME = "xxx_core_secure_session"
    private const val TOKEN_KEY = "encrypted_token"
    private const val IV_KEY = "encryption_iv"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        val existingKey = keyStore.getKey(KEY_ALIAS, null)
        if (existingKey is SecretKey) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or
                KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE
            )
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    fun saveToken(context: Context, token: String) {
        require(token.isNotBlank()) {
            "Le jeton de session est vide."
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())

        val encryptedBytes = cipher.doFinal(
            token.toByteArray(Charsets.UTF_8)
        )

        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        prefs.edit()
            .putString(
                TOKEN_KEY,
                Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            )
            .putString(
                IV_KEY,
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            )
            .apply()
    }

    fun getToken(context: Context): String? {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val encryptedToken = prefs.getString(TOKEN_KEY, null)
            ?: return null

        val encodedIv = prefs.getString(IV_KEY, null)
            ?: return null

        return try {
            val encryptedBytes = Base64.decode(
                encryptedToken,
                Base64.NO_WRAP
            )

            val iv = Base64.decode(encodedIv, Base64.NO_WRAP)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getKey(),
                GCMParameterSpec(128, iv)
            )

            String(
                cipher.doFinal(encryptedBytes),
                Charsets.UTF_8
            )
        } catch (e: Exception) {
            clear(context)
            null
        }
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
            // La suppression sera retentée lors d'une prochaine opération.
        }
    }
}
