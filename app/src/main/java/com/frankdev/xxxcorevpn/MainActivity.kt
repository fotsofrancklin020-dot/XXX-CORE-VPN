
package com.frankdev.cloudflarexuipanel

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private val blue = Color.rgb(0, 170, 255)
    private val backgroundColor = Color.rgb(8, 13, 25)
    private val workerUrl =
        "https://frankdev-xxxcore-server.fotsofrancklin020.workers.dev"

    private var adminToken: String? = null
    private lateinit var clientContainer: LinearLayout
    private lateinit var statusText: TextView

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

        statusText = TextView(this).apply {
            text = "Serveur : non connecté"
            textSize = 13f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
        }

        content.addView(brand)
        content.addView(title)
        content.addView(subtitle)
        content.addView(statusText)

        val options = listOf(
            "Administrator Login",
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
                    when (option) {
                        "Administrator Login" -> showLoginDialog()
                        "All Clients" -> loadClients("all")
                        "Active Clients" -> loadClients("active")
                        "Disabled Clients" -> loadClients("disabled")
                        else -> showMessage(option)
                    }
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 7, 0, 7)
            content.addView(button, params)
        }

        clientContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }

        content.addView(clientContainer)

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

    private fun showLoginDialog() {
        val input = EditText(this).apply {
            hint = "Secret administrateur"
            inputType =
                InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("FRANKDEV ADMIN LOGIN")
            .setMessage("Authentification administrateur")
            .setView(input)
            .setPositiveButton("CONNEXION") { _, _ ->
                val secret = input.text.toString()

                if (secret.isBlank()) {
                    showMessage("Secret administrateur obligatoire")
                } else {
                    login(secret)
                }
            }
            .setNegativeButton("ANNULER", null)
            .show()
    }

    private fun login(secret: String) {
        statusText.text = "Connexion au serveur..."

        Thread {
            try {
                val body = JSONObject()
                    .put("adminSecret", secret)
                    .toString()

                val response = callApi(
                    "POST",
                    "/admin/login",
                    body,
                    null
                )

                val result = JSONObject(response)

                if (result.optString("status") == "authenticated") {
                    adminToken = result.getString("token")

                    runOnUiThread {
                        statusText.text = "Administrateur connecté"
                        statusText.setTextColor(Color.GREEN)
                        showMessage("Authentification réussie.")
                    }
                } else {
                    runOnUiThread {
                        statusText.text = "Connexion refusée"
                        statusText.setTextColor(Color.RED)
                        showMessage(
                            result.optString(
                                "error",
                                "Authentification refusée"
                            )
                        )
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    statusText.text = "Erreur de connexion"
                    showMessage(error.message ?: "Erreur serveur")
                }
            }
        }.start()
    }

    private fun loadClients(filter: String) {
        val token = adminToken

        if (token.isNullOrBlank()) {
            showLoginDialog()
            return
        }

        statusText.text = "Chargement des clients..."
        clientContainer.removeAllViews()

        Thread {
            try {
                val response = callApi(
                    "GET",
                    "/admin/clients",
                    null,
                    token
                )

                val result = JSONObject(response)

                if (result.optString("status") != "ok") {
                    throw Exception(
                        result.optString("error", "Erreur API")
                    )
                }

                val clients = result.getJSONArray("clients")

                runOnUiThread {
                    displayClients(clients, filter)
                    statusText.text = "Serveur : connecté"
                    statusText.setTextColor(Color.GREEN)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    statusText.text = "Erreur serveur"
                    statusText.setTextColor(Color.RED)
                    showMessage(error.message ?: "Erreur inconnue")
                }
            }
        }.start()
    }

    private fun displayClients(
        clients: JSONArray,
        filter: String
    ) {
        clientContainer.removeAllViews()

        val heading = TextView(this).apply {
            text = "GESTION DES COMPTES CLIENTS"
            textSize = 17f
            setTextColor(blue)
            gravity = Gravity.CENTER
        }

        clientContainer.addView(heading)

        var displayed = 0

        for (i in 0 until clients.length()) {
            val client = clients.getJSONObject(i)

            val id = client.optString("id")
            val username = client.optString("username")
            val active = client.optInt("active") == 1

            if (filter == "active" && !active) continue
            if (filter == "disabled" && active) continue

            displayed++

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14, 14, 14, 14)
                setBackgroundColor(Color.rgb(18, 32, 52))
            }

            val name = TextView(this).apply {
                text = "Client : $username"
                textSize = 17f
                setTextColor(Color.WHITE)
            }

            val state = TextView(this).apply {
                text = if (active) "STATUT : ACTIF" else "STATUT : DÉSACTIVÉ"
                textSize = 13f
                setTextColor(
                    if (active) Color.GREEN else Color.RED
                )
            }

            val action = Button(this).apply {
                text = if (active) "DÉSACTIVER" else "ACTIVER"
                isAllCaps = false
                setOnClickListener {
                    updateClientStatus(id, !active)
                }
            }

            card.addView(name)
            card.addView(state)
            card.addView(action)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 10, 0, 10)

            clientContainer.addView(card, params)
        }

        if (displayed == 0) {
            val empty = TextView(this).apply {
                text = "Aucun client dans cette catégorie."
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }
            clientContainer.addView(empty)
        }
    }

    private fun updateClientStatus(
        clientId: String,
        active: Boolean
    ) {
        val token = adminToken ?: return

        statusText.text = "Mise à jour du compte..."

        Thread {
            try {
                val body = JSONObject()
                    .put("active", active)
                    .toString()

                val response = callApi(
                    "PATCH",
                    "/admin/clients/$clientId/status",
                    body,
                    token
                )

                val result = JSONObject(response)

                if (result.optString("status") != "updated") {
                    throw Exception(
                        result.optString(
                            "error",
                            "Modification refusée"
                        )
                    )
                }

                runOnUiThread {
                    statusText.text = "Modification réussie"
                    showMessage(
                        if (active) {
                            "Compte client activé."
                        } else {
                            "Compte client désactivé."
                        }
                    )

                    loadClients("all")
                }
            } catch (error: Exception) {
                runOnUiThread {
                    statusText.text = "Erreur de modification"
                    showMessage(error.message ?: "Erreur inconnue")
                }
            }
        }.start()
    }

    private fun callApi(
        method: String,
        path: String,
        body: String?,
        token: String?
    ): String {
        val connection = URL(workerUrl + path)
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            if (token != null) {
                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $token"
                )
            }

            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use {
                    it.write(body.toByteArray(Charsets.UTF_8))
                }
            }

            val stream =
                if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            return stream.bufferedReader().use {
                it.readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun showMessage(message: String) {
        AlertDialog.Builder(this)
            .setTitle("FRANKDEV BUILD")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
