
package com.frankdev.xxxcoreadminsigner

import android.app.Activity
import android.app.DatePickerDialog
import android.os.Bundle
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.KeyStore
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Calendar
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val requestImportKey = 4101
    private val requestSelectFile = 4102
    private val requestSaveFile = 4103
    private val keyAlias = "xxxcore_admin_vault_key"
    private var selectedContent: String? = null
    private var signedContent: String? = null
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

        val signButton = Button(this).apply {
            text = "Signer un fichier .xxxcore"
            setOnClickListener {
                if (!vaultFile.exists()) {
                    showMessage("Importe d'abord la clé privée.")
                } else {
                    openConfigPicker()
                }
            }
        }

        root.addView(title)
        root.addView(status)
        root.addView(importButton)
        root.addView(signButton)
        setContentView(root)
    }

    private fun openKeyPicker() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(picker, requestImportKey)
    }

    private fun openConfigPicker() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(picker, requestSelectFile)
    }

    @Deprecated("Retained for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        try {
            when (requestCode) {
                requestImportKey -> {
                    val pem = contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: error("Impossible de lire le fichier.")

                    require(
                        pem.contains("-----BEGIN PRIVATE KEY-----") &&
                        pem.contains("-----END PRIVATE KEY-----")
                    ) {
                        "La clé doit être au format PEM PKCS#8."
                    }

                    storeEncrypted(pem.toByteArray(Charsets.UTF_8))
                    status.text = "Clé privée importée et protégée localement."
                    showMessage("Clé importée avec succès.")
                }

                requestSelectFile -> {
                    selectedContent = contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: error("Impossible de lire le fichier.")

                    chooseExpiryDate()
                }

                requestSaveFile -> {
                    val content = signedContent
                        ?: error("Aucun fichier signé à enregistrer.")

                    contentResolver.openOutputStream(uri)?.use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("Impossible d'enregistrer le fichier.")

                    signedContent = null
                    selectedContent = null
                    status.text = "Fichier signé et enregistré."
                    showMessage("Fichier .xxxcore signé avec succès.")
                }
            }
        } catch (e: Exception) {
            showMessage(e.message ?: "Une erreur est survenue.")
        }
    }

    private fun chooseExpiryDate() {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, year, month, day ->
                try {
                    val expiry = Calendar.getInstance().apply {
                        set(year, month, day, 23, 59, 59)
                        set(Calendar.MILLISECOND, 999)
                    }.timeInMillis

                    val content = selectedContent
                        ?: error("Aucun fichier sélectionné.")

                    signedContent = signEnvelope(content, expiry)
                    openSavePicker()
                } catch (e: Exception) {
                    showMessage(e.message ?: "Signature impossible.")
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
            setTitle("Date d'expiration du fichier")
        }.show()
    }

    private fun openSavePicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "XXXCORE-signe.xxxcore")
        }
        startActivityForResult(intent, requestSaveFile)
    }

    private fun signEnvelope(
        content: String,
        expiry: Long
    ): String {
        val envelope = JSONObject(content)

        require(envelope.optString("format") == "XXXCORE") {
            "Ce fichier n'est pas un fichier XXXCORE valide."
        }

        require(envelope.optInt("version") == 1) {
            "Version du fichier non compatible."
        }

        envelope.remove("signature")
        envelope.remove("signatureAlgorithm")
        envelope.remove("expiresAt")
        envelope.put("expiresAt", expiry)

        val dataToSign = envelope.toString()
            .toByteArray(Charsets.UTF_8)

        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(loadPrivateKey())
        signer.update(dataToSign)

        val signatureBytes = signer.sign()

        envelope.put("signatureAlgorithm", "SHA256withRSA")
        envelope.put(
            "signature",
            Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
        )

        return envelope.toString(2)
    }

    private fun loadPrivateKey(): java.security.PrivateKey {
        val encrypted = vaultFile.readBytes()
        require(encrypted.size > 12) {
            "Clé privée stockée invalide."
        }

        val iv = encrypted.copyOfRange(0, 12)
        val cipherText = encrypted.copyOfRange(12, encrypted.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getExistingVaultKey(),
            GCMParameterSpec(128, iv)
        )

        val pemBytes = cipher.doFinal(cipherText)
        val pem = String(pemBytes, Charsets.UTF_8)
        val encoded = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")

        val keyBytes = Base64.decode(encoded, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)

        return KeyFactory.getInstance("RSA")
            .generatePrivate(keySpec)
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

    private fun getExistingVaultKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        return keyStore.getKey(keyAlias, null) as? SecretKey
            ?: error("Clé de protection locale introuvable.")
    }

    private fun getOrCreateVaultKey(): SecretKey {
        val existing = try {
            getExistingVaultKey()
        } catch (_: Exception) {
            null
        }

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

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
