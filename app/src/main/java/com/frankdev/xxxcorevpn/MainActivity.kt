
package com.frankdev.xxxcorevpn

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.widget.Toast
import android.net.Uri

class MainActivity : Activity() {

    private lateinit var subscriptionUrl: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "XXX-CORE VPN"
            textSize = 28f
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            text = "VPN déconnecté"
            textSize = 16f
            gravity = Gravity.CENTER
        }

        subscriptionUrl = EditText(this).apply {
            hint = "Colle ton lien d'abonnement ici"
            textSize = 14f
            setSingleLine(true)
            setText(
                getPreferences(MODE_PRIVATE)
                    .getString("subscription_url", "")
            )
        }

        val saveButton = Button(this).apply {
            text = "ENREGISTRER L'ABONNEMENT"

            setOnClickListener {
                val url = subscriptionUrl.text.toString().trim()
                val uri = Uri.parse(url)

                if (
                    (uri.scheme == "https" || uri.scheme == "http") &&
                    !uri.host.isNullOrBlank()
                ) {
                    getPreferences(MODE_PRIVATE)
                        .edit()
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
                        "Lien invalide : utilise HTTP ou HTTPS",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(subscriptionUrl)
        layout.addView(saveButton)

        setContentView(layout)
    }
}
