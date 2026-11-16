
package com.frankdev.xxxcorevpn

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("xxx_core_prefs", MODE_PRIVATE)
    }

    private val themes = arrayOf(
        "Light — Blanc et bleu",
        "Dark — Noir et violet",
        "Original — Noir et bleu électrique",
        "Premium — Noir et or"
    )

    private val options = arrayOf(
        "Custom Server",
        "Theme Settings",
        "App Settings",
        "V2ray Settings",
        "OpenVPN Settings",
        "Mobile IP Hunter",
        "SlowDns Checker",
        "Beginner Tutorial",
        "Hotspot Share",
        "IP Address Checker",
        "HostName Finder",
        "HWID",
        "Export Config",
        "Import Config",
        "Import Cloud Config",
        "Import QR Code Config",
        "Host Checker",
        "Telegram Group",
        "Optimize Battery",
        "About Info"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(this).apply {
            text = "XXX-CORE VPN SETTINGS"
            textSize = 23f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 24)
        }

        layout.addView(title)

        options.forEach { option ->
            val button = Button(this).apply {
                text = option
                setOnClickListener {
                    if (option == "Theme Settings") {
                        showThemeSelector()
                    } else {
                        Toast.makeText(
                            this@SettingsActivity,
                            "$option sera configuré dans une prochaine étape.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            layout.addView(button)
        }

        setContentView(layout)
        applySelectedTheme(layout, title)
    }

    private fun showThemeSelector() {
        val selected = prefs.getInt("selected_theme", 1)

        AlertDialog.Builder(this)
            .setTitle("Choose your theme")
            .setSingleChoiceItems(themes, selected) { dialog, which ->
                prefs.edit()
                    .putInt("selected_theme", which)
                    .apply()

                dialog.dismiss()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applySelectedTheme(
        layout: LinearLayout,
        title: TextView
    ) {
        when (prefs.getInt("selected_theme", 1)) {
            0 -> {
                layout.setBackgroundColor(Color.WHITE)
                title.setTextColor(Color.BLUE)
            }
            1 -> {
                layout.setBackgroundColor(Color.rgb(18, 18, 24))
                title.setTextColor(Color.rgb(190, 120, 255))
            }
            2 -> {
                layout.setBackgroundColor(Color.BLACK)
                title.setTextColor(Color.rgb(0, 190, 255))
            }
            3 -> {
                layout.setBackgroundColor(Color.BLACK)
                title.setTextColor(Color.rgb(230, 190, 70))
            }
        }
    }
}
