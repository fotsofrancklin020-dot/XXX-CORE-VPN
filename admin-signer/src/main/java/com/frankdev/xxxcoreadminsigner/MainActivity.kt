package com.frankdev.xxxcoreadminsigner

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val requestImportKey = 4101
    private val keyAlias = "xxxcore_admin_vault_key"
    private val vaultFile by lazy {
        File(filesDir, "admin_private_key.enc")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "XXX-CORE ADMIN SIGNER"
            textSize = 22f
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            text = if (vaultFile.exists()) {
                "Clé privée importée et stockée localement."
            } else {
                "Aucune clé privée importée."
            }
            setPadding(0, 28, 0, 28)
        }

        val importButton = Button(this).apply {
            text = "Importer la clé privée"
            setOnClickListener {
                openKeyPicker()
            }
        }

        root.addView(title)
        root.addView(status)
        root.addView(importButton)
        setContentView(root)
    }

    private fun openKeyPicker() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(picker, requestImportKey)
    }

    @Deprecated("Retained for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != requestImportKey || resultCode != RESULT_OK) {
            return
        }

        val uri = data?.data ?: return

        try {
            val pem = contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Impossible de lire le fichier.")

            require(
                pem.contains("BEGIN PRIVATE KEY") &&
                pem.contains("END PRIVATE KEY")
            ) {
                "La clé doit être au format PEM PKCS#8."
            }

            storeEncrypted(pem.toByteArray(Charsets.UTF_8))
            status.text = "Clé privée importée et protégée localement."

            Toast.makeText(
                this,
                "Clé importée avec succès.",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                e.message ?: "Importation impossible.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun storeEncrypted(plain: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            getOrCreateVaultKey()
        )
        val encrypted = cipher.doFinal(plain)
        vaultFile.writeBytes(cipher.iv + encrypted)
    }

    private fun getOrCreateVaultKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setKeySize(256)
                .build()
        )

        return generator.generateKey()
    }
}
