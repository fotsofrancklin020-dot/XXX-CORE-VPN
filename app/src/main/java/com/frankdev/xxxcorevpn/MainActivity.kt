
package com.frankdev.xxxcorevpn

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import org.json.JSONObject
import android.net.VpnService
import android.os.Bundle
import android.widget.ScrollView
import android.text.InputType
import android.widget.ImageView
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.net.Uri

class MainActivity : Activity() {

    private val vpnRequestCode = 1001
    private val configRequestCode = 1002
    private val exportRequestCode = 1003

    private var pendingExportContent: String? = null

    private lateinit var subscriptionUrl: EditText
    private lateinit var udpServer: EditText
    private lateinit var udpUsername: EditText
    private lateinit var udpPassword: EditText
    private lateinit var udpSni: EditText
    private lateinit var status: TextView

    private var shouldRefreshTheme = false

    override fun onResume() {
        super.onResume()

        if (shouldRefreshTheme) {
            shouldRefreshTheme = false
            recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getPreferences(MODE_PRIVATE)

        val themePrefs = getSharedPreferences(
            "xxx_core_prefs",
            MODE_PRIVATE
        )

        val rootLayout = FrameLayout(this)

        // DRAWER
        val drawerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(
                android.graphics.Color.rgb(18, 18, 24)
            )
        }

        // DASHBOARD
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        // THEMES
        val selectedTheme = themePrefs.getInt("selected_theme", 1)

        val backgroundColor: Int
        val accentColor: Int
        val textColor: Int

        when (selectedTheme) {
            0 -> {
                backgroundColor = android.graphics.Color.WHITE
                accentColor = android.graphics.Color.BLUE
                textColor = android.graphics.Color.BLACK
            }
            1 -> {
                backgroundColor = android.graphics.Color.rgb(18, 18, 24)
                accentColor = android.graphics.Color.rgb(190, 120, 255)
                textColor = android.graphics.Color.WHITE
            }
            2 -> {
                backgroundColor = android.graphics.Color.BLACK
                accentColor = android.graphics.Color.rgb(0, 190, 255)
                textColor = android.graphics.Color.WHITE
            }
            else -> {
                backgroundColor = android.graphics.Color.BLACK
                accentColor = android.graphics.Color.rgb(230, 190, 70)
                textColor = android.graphics.Color.WHITE
            }
        }

        // DRAWER LOGO
        val drawerLogo = ImageView(this).apply {
            setImageResource(R.drawable.xxx_core_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setPadding(8, 16, 8, 8)
        }

        drawerLayout.addView(
            drawerLogo,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (220 * resources.displayMetrics.density).toInt()
            )
        )

        // BRAND
        val drawerBrand = TextView(this).apply {
            text = "XXX-CORE VPN\nFRANKDEV BUILD"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(0, 190, 255))
            setPadding(0, 8, 0, 24)
        }

        drawerLayout.addView(drawerBrand)

        fun styleDrawerButton(button: Button, icon: Int) {
            button.textSize = 16f
            button.setCompoundDrawablesWithIntrinsicBounds(
                icon, 0, 0, 0
            )
            button.compoundDrawablePadding = 16
            button.gravity = Gravity.CENTER
        }

        // CUSTOM SERVER
        val customServerButton = Button(this).apply {
            text = "Custom Server"
            styleDrawerButton(this, android.R.drawable.ic_menu_edit)

            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "Custom Server",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        drawerLayout.addView(customServerButton)

        // THEME SETTINGS
        val themeSettingsButton = Button(this).apply {
            text = "Theme Settings"
            styleDrawerButton(this, android.R.drawable.ic_menu_preferences)

            setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, SettingsActivity::class.java)
                )
            }
        }
        drawerLayout.addView(themeSettingsButton)

        // APP SETTINGS
        val appSettingsButton = Button(this).apply {
            text = "App Settings"
            styleDrawerButton(this, android.R.drawable.ic_menu_manage)

            setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, SettingsActivity::class.java)
                )
            }
        }
        drawerLayout.addView(appSettingsButton)

        // OTHER DRAWER OPTIONS
        fun addDrawerOption(label: String, icon: Int) {
            val optionButton = Button(this).apply {
                text = label
                styleDrawerButton(this, icon)

                setOnClickListener {
                    when (label) {
                        "Import Config" -> showImportOptionsDialog()
                        "Export Config" -> {
                            startActivity(
                                Intent(
                                    this@MainActivity,
                                    ExportConfigActivity::class.java
                                )
                            )
                        }
                        else -> Toast.makeText(
                            this@MainActivity,
                            "$label sera configuré dans une prochaine étape",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            drawerLayout.addView(optionButton)
        }

        addDrawerOption("V2ray Settings", android.R.drawable.ic_menu_share)
        addDrawerOption("OpenVPN Settings", android.R.drawable.ic_lock_lock)
        addDrawerOption("Mobile IP Hunter", android.R.drawable.ic_menu_mylocation)
        addDrawerOption("SlowDns Checker", android.R.drawable.ic_menu_search)
        addDrawerOption("Beginner Tutorial", android.R.drawable.ic_menu_help)
        addDrawerOption("Hotspot Share", android.R.drawable.ic_menu_upload)
        addDrawerOption("IP Address Checker", android.R.drawable.ic_menu_info_details)
        addDrawerOption("HostName Finder", android.R.drawable.ic_menu_compass)
        addDrawerOption("HWID", android.R.drawable.ic_menu_view)
        addDrawerOption("Export Config", android.R.drawable.ic_menu_save)
        addDrawerOption("Import Config", android.R.drawable.ic_menu_add)
        addDrawerOption("Import Cloud Config", android.R.drawable.ic_menu_upload)
        addDrawerOption("Import QR Code Config", android.R.drawable.ic_menu_camera)
        addDrawerOption("Host Checker", android.R.drawable.ic_menu_directions)
        addDrawerOption("Telegram Group", android.R.drawable.ic_menu_send)
        addDrawerOption("Optimize Battery", android.R.drawable.ic_menu_manage)
        addDrawerOption("About Info", android.R.drawable.ic_menu_info_details)

        // DRAWER SCROLL
        val drawerScrollView = ScrollView(this).apply {
            isFillViewport = true
            visibility = View.GONE
            clipToPadding = false
        }

        drawerScrollView.addView(
            drawerLayout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // MENU BUTTON
        val menuButton = Button(this).apply {
            text = "☰"
            textSize = 24f

            setOnClickListener {
                drawerScrollView.visibility =
                    if (drawerScrollView.visibility == View.VISIBLE) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
            }
        }

        // TITLE
        val title = TextView(this).apply {
            text = "XXX-CORE VPN"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(accentColor)
        }

        // VPN STATUS
        status = TextView(this).apply {
            text = "VPN déconnecté"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(textColor)
        }

        // CONNECT BUTTON
        val connectButton = Button(this).apply {
            text = "CONNECTER"

            setOnClickListener {
                val vpnIntent = VpnService.prepare(this@MainActivity)

                if (vpnIntent != null) {
                    startActivityForResult(vpnIntent, vpnRequestCode)
                } else {
                    showVpnAuthorizationGranted()
                }
            }
        }

        // SETTINGS BUTTON
        val settingsButton = Button(this).apply {
            text = "⚙ PARAMÈTRES"

            setOnClickListener {
                shouldRefreshTheme = true

                startActivity(
                    Intent(this@MainActivity, SettingsActivity::class.java)
                )
            }
        }

        // SUBSCRIPTION
        subscriptionUrl = EditText(this).apply {
            hint = "Lien d'abonnement HTTP/HTTPS"
            setSingleLine(true)
            setText(prefs.getString("subscription_url", ""))
        }

        val saveSubscription = Button(this).apply {
            text = "ENREGISTRER L'ABONNEMENT"

            setOnClickListener {
                val url = subscriptionUrl.text.toString().trim()
                val uri = Uri.parse(url)

                if (
                    (uri.scheme == "https" || uri.scheme == "http") &&
                    !uri.host.isNullOrBlank()
                ) {
                    prefs.edit()
                        .putString("subscription_url", url)
                        .apply()

                    Toast.makeText(
                        this@MainActivity,
                        "Abonnement enregistré",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Lien invalide",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // UDP CUSTOM
        val udpTitle = TextView(this).apply {
            text = "UDP CUSTOM"
            textSize = 22f
            gravity = Gravity.CENTER
        }

        udpServer = EditText(this).apply {
            hint = "Server host:port"
            setSingleLine(true)
            setText(prefs.getString("udp_server", ""))
        }

        udpUsername = EditText(this).apply {
            hint = "Username"
            setSingleLine(true)
            setText(prefs.getString("udp_username", ""))
        }

        udpPassword = EditText(this).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setText(prefs.getString("udp_password", ""))
        }

        udpSni = EditText(this).apply {
            hint = "SNI (facultatif)"
            setSingleLine(true)
            setText(prefs.getString("udp_sni", ""))
        }

        val saveUdp = Button(this).apply {
            text = "ENREGISTRER UDP CUSTOM"

            setOnClickListener {
                val server = udpServer.text.toString().trim()
                val username = udpUsername.text.toString().trim()
                val password = udpPassword.text.toString()

                if (
                    server.isBlank() ||
                    !server.contains(":") ||
                    server.contains(" ") ||
                    username.isBlank() ||
                    password.isBlank()
                ) {
                    Toast.makeText(
                        this@MainActivity,
                        "Vérifie le serveur et les identifiants",
                        Toast.LENGTH_LONG
                    ).show()

                    return@setOnClickListener
                }

                prefs.edit()
                    .putString("udp_server", server)
                    .putString("udp_username", username)
                    .putString("udp_password", password)
                    .putString("udp_sni", udpSni.text.toString().trim())
                    .apply()

                Toast.makeText(
                    this@MainActivity,
                    "Configuration UDP enregistrée",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // DASHBOARD ELEMENTS
        layout.addView(menuButton)
        layout.addView(title)
        layout.addView(status)
        layout.addView(connectButton)
        layout.addView(settingsButton)
        layout.addView(subscriptionUrl)
        layout.addView(saveSubscription)
        layout.addView(udpTitle)
        layout.addView(udpServer)
        layout.addView(udpUsername)
        layout.addView(udpPassword)
        layout.addView(udpSni)
        layout.addView(saveUdp)

        layout.setBackgroundColor(backgroundColor)

        rootLayout.addView(
            layout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val drawerParams = FrameLayout.LayoutParams(
            (280 * resources.displayMetrics.density).toInt(),
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.START
        )

        rootLayout.addView(drawerScrollView, drawerParams)

        setContentView(rootLayout)
    }

    // OPEN ANDROID FILE PICKER
    private fun openConfigPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/json",
                    "text/plain",
                    "application/octet-stream"
                )
            )
        }

        try {
            startActivityForResult(intent, configRequestCode)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Impossible d'ouvrir les fichiers.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // READ AND PARSE SELECTED CONFIG
    private fun importSelectedConfig(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: throw IllegalArgumentException(
                    "Impossible de lire ce fichier."
                )

            require(content.isNotBlank()) {
                "Le fichier est vide."
            }

            val config = VmessLinkParser.parse(content)

            val outbound = VmessLinkParser
                .toSingBoxOutboundJson(config)
                .toString(2)

            getPreferences(MODE_PRIVATE).edit()
                .putString("imported_vmess_outbound", outbound)
                .putString("imported_vmess_name", config.name)
                .apply()

            showImportedConfig(config)

        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Échec de l'importation")
                .setMessage(
                    e.message ?: "Configuration invalide ou non prise en charge."
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // DISPLAY IMPORTED CONFIG
    private fun showImportedConfig(config: VmessConfig) {
        val details = """
            Nom : ${config.name.ifBlank { "Sans nom" }}

            Protocole : VMess
            Serveur : ${config.server}
            Port : ${config.port}
            UUID : ${config.uuid}
            Alter ID : ${config.alterId}
            Sécurité : ${config.security}

            Transport : ${config.transport}
            Host : ${config.host.ifBlank { "Non défini" }}
            Path : ${config.path.ifBlank { "Non défini" }}

            TLS : ${if (config.tlsEnabled) "Activé" else "Désactivé"}
            SNI : ${config.serverName.ifBlank { "Non défini" }}
            ALPN : ${config.alpn.joinToString().ifBlank { "Non défini" }}

            Configuration analysée et enregistrée localement.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Configuration VMess importée")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()

        Toast.makeText(
            this,
            "Importation VMess réussie",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ACTIVITY RESULTS
    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == configRequestCode) {
            if (resultCode == RESULT_OK) {
                val uri = data?.data

                if (uri != null) {
                    importSelectedConfig(uri)
                } else {
                    Toast.makeText(
                        this,
                        "Aucun fichier sélectionné.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            return
        }

        if (requestCode == vpnRequestCode) {
            if (resultCode == RESULT_OK) {
                showVpnAuthorizationGranted()
            } else {
                status.text = "VPN déconnecté"

                Toast.makeText(
                    this,
                    "Autorisation VPN refusée",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // VPN AUTHORIZATION
    private fun showVpnAuthorizationGranted() {
        status.text = "Autorisation accordée — moteur à démarrer"

        Toast.makeText(
            this,
            "Autorisation VPN accordée",
            Toast.LENGTH_SHORT
        ).show()
    }
}
