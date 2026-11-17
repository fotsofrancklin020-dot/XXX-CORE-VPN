

package com.frankdev.xxxcorevpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.text.InputType
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

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
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

        val title = TextView(this).apply {
            text = "XXX-CORE VPN"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(accentColor)
        }

        status = TextView(this).apply {
            text = "VPN déconnecté"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(textColor)
        }

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

val settingsButton = Button(this).apply {
    text = "⚙ PARAMÈTRES"

    setOnClickListener {
        shouldRefreshTheme = true

        startActivity(
            Intent(this@MainActivity, SettingsActivity::class.java)
        )
    }
}
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
                    .putString(
                        "udp_sni",
                        udpSni.text.toString().trim()
                    )
                    .apply()

                Toast.makeText(
                    this@MainActivity,
                    "Configuration UDP enregistrée",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

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
        setContentView(layout)
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: android.content.Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

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

    private fun showVpnAuthorizationGranted() {
        status.text = "Autorisation accordée — moteur à démarrer"

        Toast.makeText(
            this,
            "Autorisation VPN accordée",
            Toast.LENGTH_SHORT
        ).show()
    }
}


                

