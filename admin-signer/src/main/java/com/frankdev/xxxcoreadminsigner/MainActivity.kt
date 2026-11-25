
package com.frankdev.cloudflarexuipanel

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private val blue = Color.rgb(0, 170, 255)
    private val backgroundColor = Color.rgb(8, 13, 25)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        showDashboard()
    }

    private fun showDashboard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 30, 24, 24)
            setBackgroundColor(backgroundColor)
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val brand = TextView(this).apply {
            text = "FRANKDEV BUILD"
            textSize = 15f
            setTextColor(blue)
            gravity = Gravity.CENTER
        }

        val title = TextView(this).apply {
            text = "\nCLOUDFLARE\nX-UI PANEL"
            textSize = 29f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "\nADMINISTRATOR DASHBOARD\n"
            textSize = 13f
            setTextColor(blue)
            gravity = Gravity.CENTER
        }

        content.addView(brand)
        content.addView(title)
        content.addView(subtitle)

        val options = listOf(
            "Dashboard",
            "Create Client",
            "All Clients",
            "Active Clients",
            "Disabled Clients",
            "Online Clients",
            "Disconnect Client",
            "Subscription Manager",
            "Data Usage / Traffic Monitor",
            "Set Data Limit",
            "Set Expiration Date",
            "Renew Subscription",
            "Protocol Management",
            "Default Mode Management",
            "Server Management",
            "Connection History",
            "Notifications",
            "Admin Security",
            "Backup & Restore",
            "About Info"
        )

        for (option in options) {
            val button = Button(this).apply {
                text = option
                textSize = 14f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(18, 42, 70))
                isAllCaps = false
                setOnClickListener {
                    showMessage(option)
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 7, 0, 7)
            content.addView(button, params)
        }

        val footer = TextView(this).apply {
            text = "\nSECURITY BY FRANKDEV\n© 2026 FRANKDEV BUILD"
            textSize = 12f
            setTextColor(blue)
            gravity = Gravity.CENTER
        }

        content.addView(footer)
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun showMessage(option: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle(option)
            .setMessage("Cette fonctionnalité sera connectée au serveur Cloudflare dans les prochaines étapes.")
            .setPositiveButton("OK", null)
            .show()
    }
}
