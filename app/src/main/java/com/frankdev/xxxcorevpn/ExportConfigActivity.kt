
package com.frankdev.xxxcorevpn

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ExportConfigActivity : Activity() {
    private val gold = Color.rgb(255, 190, 35)
    private val cyan = Color.rgb(0, 190, 255)
    private val black = Color.rgb(8, 8, 12)
    private val dark = Color.rgb(20, 20, 25)
    private val white = Color.WHITE
    private val exportRequestCode = 1103

    private val options = linkedMapOf(
        "Anti-Sniff" to true,
        "PS Install Only" to false,
        "Mobile Data" to false,
        "HWID Lock" to false,
        "Block Root" to false,
        "Expire Date" to false,
        "Password" to false,
        "Export Custom Server" to false
    )

    private lateinit var passwordInput: EditText
    private lateinit var expiryButton: Button
    private var expiryDate = ""
    private var pendingContent: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
            setBackgroundColor(black)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }

        val heading = TextView(this).apply {
            text = "XXX-CORE VPN\nEXPORT CONFIG"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(gold)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(18))
        }
        page.addView(heading)

        val shield = TextView(this).apply {
            text = "♛  X  ♛"
            textSize = 42f
            gravity = Gravity.CENTER
            setTextColor(cyan)
            setPadding(0, dp(4), 0, dp(12))
        }
        page.addView(shield)

        val panelTitle = TextView(this).apply {
            text = "🔒  PROTEXTRAS                         8 OPTIONS"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(gold)
            setBackgroundColor(dark)
            setPadding(dp(12), dp(16), dp(8), dp(16))
        }
        page.addView(panelTitle)

        val optionsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.rgb(13, 13, 17))
        }
        page.addView(optionsPanel)

        options.forEach { (label, initial) ->
            val toggle = Switch(this).apply {
                text = label
                textSize = 16f
                setTextColor(white)
                isChecked = initial
                setPadding(dp(8), dp(10), dp(8), dp(10))
                buttonTintList = android.content.res.ColorStateList.valueOf(cyan)
                setOnCheckedChangeListener { _, checked ->
                    options[label] = checked

                    if (label == "Password") {
                        passwordInput.visibility =
                            if (checked) View.VISIBLE else View.GONE
                    }

                    if (label == "Expire Date") {
                        expiryButton.visibility =
                            if (checked) View.VISIBLE else View.GONE
                    }
                }
            }
            optionsPanel.addView(toggle)

            val separator = View(this).apply {
                setBackgroundColor(Color.rgb(75, 62, 35))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1)
                )
            }
            optionsPanel.addView(separator)
        }

        passwordInput = EditText(this).apply {
            hint = "Mot de passe du fichier"
            setHintTextColor(Color.LTGRAY)
            setTextColor(white)
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(dp(12), dp(8), dp(12), dp(8))
            visibility = View.GONE
            setBackgroundColor(dark)
        }
        page.addView(passwordInput, matchWidth())

        expiryButton = Button(this).apply {
            text = "CHOISIR LA DATE D'EXPIRATION"
            setTextColor(black)
            setBackgroundColor(gold)
            visibility = View.GONE
            setOnClickListener { showExpiryPicker() }
        }
        page.addView(expiryButton, matchWidth())

        val generate = Button(this).apply {
            text = "⚡  GENERATE XXXCORE FILE  ❯"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(gold)
            setBackgroundColor(Color.rgb(35, 25, 5))
            setPadding(dp(8), dp(18), dp(8), dp(18))
            setOnClickListener { prepareExport() }
        }
        val generateParams = matchWidth()
        generateParams.topMargin = dp(18)
        page.addView(generate, generateParams)

        val qrButton = Button(this).apply {
            text = "QR CODE"
            setTextColor(cyan)
            setBackgroundColor(dark)
            setOnClickListener {
                Toast.makeText(
                    this@ExportConfigActivity,
                    "L'export QR sera ajouté dans une prochaine étape.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        val cloudButton = Button(this).apply {
            text = "EXPORT TO CLOUD"
            setTextColor(cyan)
            setBackgroundColor(dark)
            setOnClickListener { prepareExport() }
        }

        val shareButton = Button(this).apply {
            text = "PARTAGER"
            setTextColor(cyan)
            setBackgroundColor(dark)
            setOnClickListener { shareExportedFile() }
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(qrButton, LinearLayout.LayoutParams(0, dp(54), 1f))
            addView(cloudButton, LinearLayout.LayoutParams(0, dp(54), 1f))
            addView(shareButton, LinearLayout.LayoutParams(0, dp(54), 1f))
        }
        val actionsParams = matchWidth()
        actionsParams.topMargin = dp(12)
        page.addView(actions, actionsParams)

        val footer = TextView(this).apply {
            text = "★  FRANKDEV BUILD  ★"
            textSize = 18f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(gold)
            setPadding(0, dp(24), 0, dp(8))
        }
        page.addView(footer)

        setContentView(scroll)
    }

    private fun showExpiryPicker() {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day)
                expiryDate = SimpleDateFormat(
                    "dd/MM/yyyy",
                    Locale.getDefault()
                ).format(calendar.time)

                expiryButton.text = "EXPIRY DATE : $expiryDate"
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun prepareExport() {
        try {
            if (options["Password"] == true &&
                passwordInput.text.isNullOrBlank()
            ) {
                passwordInput.error = "Entre un mot de passe"
                return
            }

            if (options["Expire Date"] == true && expiryDate.isBlank()) {
                Toast.makeText(
                    this,
                    "Choisis une date d'expiration.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val prefs = getSharedPreferences("MainActivity", MODE_PRIVATE)

            val payload = JSONObject()
                .put(
                    "subscription_url",
                    prefs.getString("subscription_url", "") ?: ""
                )
                .put(
                    "imported_vmess_outbound",
                    prefs.getString("imported_vmess_outbound", "") ?: ""
                )
                .put(
                    "imported_vmess_name",
                    prefs.getString("imported_vmess_name", "") ?: ""
                )
                .put("protextras", JSONObject(options))
                .put(
                    "expiry_date",
                    if (options["Expire Date"] == true) expiryDate else ""
                )

            if (options["Export Custom Server"] == true) {
                payload.put(
                    "udp_server",
                    prefs.getString("udp_server", "") ?: ""
                )
                payload.put(
                    "udp_username",
                    prefs.getString("udp_username", "") ?: ""
                )
                payload.put(
                    "udp_password",
                    prefs.getString("udp_password", "") ?: ""
                )
                payload.put(
                    "udp_sni",
                    prefs.getString("udp_sni", "") ?: ""
                )
            }

            val password = if (options["Password"] == true) {
                passwordInput.text.toString()
            } else {
                null
            }

            pendingContent = XxxCoreFile.create(payload, password)

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(
                    Intent.EXTRA_TITLE,
                    "XXXCORE-config.xxxcore"
                )
            }

            startActivityForResult(intent, exportRequestCode)

        } catch (e: Exception) {
            Toast.makeText(
                this,
                e.message ?: "Export impossible.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun shareExportedFile() {
        val content = pendingContent

        if (content.isNullOrBlank()) {
            Toast.makeText(
                this,
                "Génère d'abord le fichier .xxxcore.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, content)
        }

        startActivity(Intent.createChooser(send, "Partager XXXCORE"))
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != exportRequestCode) return

        if (resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            val content = pendingContent

            if (uri != null && content != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException(
                        "Impossible d'enregistrer le fichier."
                    )

                    Toast.makeText(
                        this,
                        "Fichier .xxxcore exporté.",
                        Toast.LENGTH_LONG
                    ).show()

                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        e.message ?: "Échec de l'export.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        pendingContent = null
    }

    private fun matchWidth(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
