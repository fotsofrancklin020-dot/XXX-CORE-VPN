
package com.frankdev.xxxcorevpn

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object XxxCoreFile {

    private const val FORMAT = "XXXCORE"
    private const val VERSION = 1
    private const val ITERATIONS = 210000
    private const val KEY_LENGTH = 256
    private const val TAG_LENGTH = 128

    private val aad = "$FORMAT:$VERSION".toByteArray(Charsets.UTF_8)

    fun create(payload: JSONObject, password: String? = null): String {
        val envelope = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)

        if (password.isNullOrEmpty()) {
            envelope.put("encrypted", false)
            envelope.put("payload", payload)
            return envelope.toString(2)
        }

        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }

        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            key,
            GCMParameterSpec(TAG_LENGTH, iv)
        )
        cipher.updateAAD(aad)

        val encryptedData = cipher.doFinal(
            payload.toString().toByteArray(Charsets.UTF_8)
        )

        envelope.put("encrypted", true)
        envelope.put("iterations", ITERATIONS)
        envelope.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        envelope.put("data", Base64.encodeToString(encryptedData, Base64.NO_WRAP))

        return envelope.toString(2)
    }

    fun read(content: String, password: String? = null): JSONObject {
        val envelope = JSONObject(content)

        require(envelope.optString("format") == FORMAT) {
            "Ce fichier n'est pas un fichier XXXCORE valide."
        }

        require(envelope.optInt("version") == VERSION) {
            "Version du fichier XXXCORE non compatible."
        }

        if (!envelope.optBoolean("encrypted", false)) {
            return envelope.getJSONObject("payload")
        }

        if (password.isNullOrEmpty()) {
            throw IllegalArgumentException(
                "Ce fichier .xxxcore est protégé par un mot de passe."
            )
        }

        val iterations = envelope.optInt("iterations", ITERATIONS)
        require(iterations in 100000..1000000) {
            "Paramètres de chiffrement invalides."
        }

        val salt = Base64.decode(envelope.getString("salt"), Base64.DEFAULT)
        val iv = Base64.decode(envelope.getString("iv"), Base64.DEFAULT)
        val data = Base64.decode(envelope.getString("data"), Base64.DEFAULT)

        require(salt.size == 16 && iv.size == 12) {
            "Fichier XXXCORE endommagé."
        }

        val key = deriveKey(password, salt, iterations)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TAG_LENGTH, iv)
        )
        cipher.updateAAD(aad)

        val decrypted = cipher.doFinal(data)

        return JSONObject(String(decrypted, Charsets.UTF_8))
    }

    private fun deriveKey(
        password: String,
        salt: ByteArray,
        iterations: Int = ITERATIONS
    ): SecretKeySpec {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            iterations,
            KEY_LENGTH
        )

        return try {
            val bytes = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded

            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
