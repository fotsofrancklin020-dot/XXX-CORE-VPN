
package com.frankdev.xxxcorevpn

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

class MainActivity : Activity() {

    private val vpnRequestCode = 1001
    private val configRequestCode = 1002
    private val exportRequestCode = 1003

    private var pendingExportContent: String? = null
    private var vpnButton: Button? = null

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
                    Intent(
                        this@MainActivity,
                        SettingsActivity::class.java
                    )
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
                    Intent(
                        this@MainActivity,
                        SettingsActivity::class.java
                    )
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

        // CONNECT / DISCONNECT BUTTON
        val connectButton = Button(this).apply {
            text = "CONNECTER"

            setOnClickListener {
                if (text.toString() == "DÉCONNECTER") {

                    val stopIntent = Intent(
                        this@MainActivity,
                        FrankVpnService::class.java
                    ).apply {
                        action = FrankVpnService.ACTION_DISCONNECT
                    }

                    startService(stopIntent)

                    text = "CONNECTER"
                    status.text = "VPN déconnecté"

                } else {
                    val vpnIntent =
                        VpnService.prepare(this@MainActivity)

                    if (vpnIntent != null) {
                        startActivityForResult(
                            vpnIntent,
                            vpnRequestCode
                        )
                    } else {
                        showVpnAuthorizationGranted()
                    }
                }
            }
        }

        vpnButton = connectButton

        // SETTINGS BUTTON
        val settingsButton = Button(this).apply {
            text = "⚙ PARAMÈTRES"

            setOnClickListener {
                shouldRefreshTheme = true

                startActivity(
                    Intent(
                        this@MainActivity,
                        SettingsActivity::class.java
                    )
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

    // IMPORT CONFIG OPTIONS
    private fun showImportOptionsDialog() {
        val options = arrayOf(
            "VMess / VLESS Link",
            "XXXCORE File"
        )

        AlertDialog.Builder(this)
            .setTitle("IMPORT CONFIG")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showLinkImportDialog()
                    1 -> openConfigPicker()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // IMPORT VMESS / VLESS LINK
    private fun showLinkImportDialog() {
        val linkInput = EditText(this).apply {
            hint = "Colle ton lien vmess:// ou vless://"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            setPadding(24, 16, 24, 16)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("IMPORT VMess / VLESS")
            .setView(linkInput)
            .setPositiveButton("Importer", null)
            .setNegativeButton("Annuler", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val link = linkInput.text.toString().trim()

                    try {
                        require(link.isNotBlank()) {
                            "Colle un lien avant de continuer."
                        }

                        when {
                            link.startsWith("vmess://", true) -> {
                                val config = VmessLinkParser.parse(link)

                                val outbound =
                                    VmessLinkParser.toSingBoxOutboundJson(config)

                                val configName = config.name.ifBlank {
                                    "${config.server}:${config.port}"
                                }

                                getPreferences(MODE_PRIVATE).edit()
                                    .putString(
                                        "imported_vmess_outbound",
                                        outbound.toString(2)
                                    )
                                    .putString(
                                        "imported_vmess_name",
                                        configName
                                    )
                                    .apply()

                                dialog.dismiss()
                                showImportedConfig(config)
                            }

                            link.startsWith("vless://", true) -> {
                                val outbound = VlessLinkParser.parse(link)

                                val configName =
                                    VlessLinkParser.getName(link).ifBlank {
                                        "${outbound.getString("server")}:" +
                                            outbound.getInt("server_port")
                                    }

                                getPreferences(MODE_PRIVATE).edit()
                                    .putString(
                                        "imported_vmess_outbound",
                                        outbound.toString(2)
                                    )
                                    .putString(
                                        "imported_vmess_name",
                                        configName
                                    )
                                    .apply()

                                dialog.dismiss()

                                AlertDialog.Builder(this)
                                    .setTitle("Configuration VLESS importée")
                                    .setMessage(
                                        "Nom : $configName\n\n" +
                                            "Protocole : VLESS\n" +
                                            "Serveur : ${outbound.getString("server")}\n" +
                                            "Port : ${outbound.getInt("server_port")}\n\n" +
                                            "Configuration enregistrée localement."
                                    )
                                    .setPositiveButton("OK", null)
                                    .show()

                                Toast.makeText(
                                    this,
                                    "Importation VLESS réussie",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            else -> {
                                throw IllegalArgumentException(
                                    "Lien non reconnu. Utilise vmess:// ou vless://."
                                )
                            }
                        }

                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            e.message ?: "Échec de l'importation.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        dialog.show()
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

    // READ AND IMPORT XXXCORE FILE
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

            XxxCoreSignatureVerifier.verify(content)

            val envelope = JSONObject(content)

            if (envelope.optBoolean("encrypted", false)) {
                showXxxCorePasswordDialog(content)
            } else {
                completeXxxCoreImport(content, null)
            }

        } catch (e: Exception) {
            showImportError(
                e.message ?: "Fichier XXXCORE invalide."
            )
        }
    }

    // XXXCORE PASSWORD DIALOG
    private fun showXxxCorePasswordDialog(content: String) {
        val passwordInput = EditText(this).apply {
            hint = "Mot de passe du fichier"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Fichier XXXCORE protégé")
            .setMessage(
                "Entre le mot de passe pour déchiffrer ce fichier."
            )
            .setView(passwordInput)
            .setPositiveButton("Déverrouiller", null)
            .setNegativeButton("Annuler", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val password = passwordInput.text.toString()

                    if (password.isBlank()) {
                        passwordInput.error = "Mot de passe obligatoire"
                        return@setOnClickListener
                    }

                    try {
                        completeXxxCoreImport(content, password)
                        dialog.dismiss()
                    } catch (e: Exception) {
                        passwordInput.error =
                            e.message ?: "Mot de passe incorrect"
                    }
                }
        }

        dialog.show()
    }

    // RESTORE VERIFIED XXXCORE CONFIG
    private fun completeXxxCoreImport(
        content: String,
        password: String?
    ) {
        XxxCoreSignatureVerifier.verify(content)

        val envelope = JSONObject(content)
        val payload = XxxCoreFile.read(content, password)
        val expiry = envelope.getLong("expiresAt")

        val prefs = getPreferences(MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putString(
            "subscription_url",
            payload.optString("subscription_url", "")
        )

        editor.putString(
            "imported_vmess_outbound",
            payload.optString("imported_vmess_outbound", "")
        )

        editor.putString(
            "imported_vmess_name",
            payload.optString("imported_vmess_name", "")
        )

        editor.putString(
            "expiry_date",
            payload.optString("expiry_date", "")
        )

        editor.putLong("xxxcore_expires_at", expiry)

        val protextras = payload.optJSONObject("protextras")
        if (protextras != null) {
            editor.putString("protextras", protextras.toString())
        }

        if (payload.has("udp_server")) {
            editor.putString(
                "udp_server",
                payload.optString("udp_server")
            )
            editor.putString(
                "udp_username",
                payload.optString("udp_username")
            )
            editor.putString(
                "udp_password",
                payload.optString("udp_password")
            )
            editor.putString(
                "udp_sni",
                payload.optString("udp_sni")
            )
        }

        editor.apply()

        subscriptionUrl.setText(
            payload.optString("subscription_url", "")
        )

        if (payload.has("udp_server")) {
            udpServer.setText(payload.optString("udp_server"))
            udpUsername.setText(payload.optString("udp_username"))
            udpPassword.setText(payload.optString("udp_password"))
            udpSni.setText(payload.optString("udp_sni"))
        }

        AlertDialog.Builder(this)
            .setTitle("XXXCORE importé avec succès")
            .setMessage(
                "Configuration vérifiée et enregistrée.\n\n" +
                    "Nom : ${payload.optString("imported_vmess_name", "Sans nom")}\n" +
                    "Expiration : ${payload.optString("expiry_date", "Non précisée")}\n\n" +
                    "Signature Admin Signer valide."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // DISPLAY IMPORT ERROR
    private fun showImportError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Échec de l'importation")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // DISPLAY IMPORTED VMESS CONFIG
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

    // VPN AUTHORIZATION AND ENGINE START
    private fun showVpnAuthorizationGranted() {

        val prefs = getPreferences(MODE_PRIVATE)

        val outboundJson = prefs.getString(
            "imported_vmess_outbound",
            null
        )

        if (outboundJson.isNullOrBlank()) {
            status.text = "Aucune configuration importée"

            Toast.makeText(
                this,
                "Importe d'abord une configuration VMess ou VLESS",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        try {
            JSONObject(outboundJson)

            val serviceIntent = Intent(
                this,
                FrankVpnService::class.java
            ).apply {
                action = FrankVpnService.ACTION_CONNECT

                putExtra(
                    FrankVpnService.EXTRA_OUTBOUND_JSON,
                    outboundJson
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            vpnButton?.text = "DÉCONNECTER"
            status.text = "Démarrage du moteur VPN..."

            Toast.makeText(
                this,
                "Démarrage de XXX-CORE VPN",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {

            status.text = "Erreur de démarrage VPN"

            Toast.makeText(
                this,
                "Erreur : ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
