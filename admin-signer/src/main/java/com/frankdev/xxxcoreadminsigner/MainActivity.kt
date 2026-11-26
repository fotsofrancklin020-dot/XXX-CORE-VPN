
package com.frankdev.cloudflarexuipanel

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.animation.ValueAnimator
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.os.Bundle
import android.net.Uri
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private val blue = Color.rgb(0, 170, 255)
    private val cyan = Color.rgb(0, 230, 255)
    private val green = Color.rgb(35, 235, 135)
    private val red = Color.rgb(255, 65, 85)
    private val grey = Color.rgb(155, 165, 180)
    private val dark = Color.rgb(8, 13, 25)

    private val workerUrl =
        "https://frankdev-xxxcore-server.fotsofrancklin020.workers.dev"

    private var adminToken = ""
    private lateinit var drawer: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var statusText: TextView
    private var currentScreen = "dashboard"
    private var pulseCounter = 0

    private val options = listOf(
        "Tableau de bord",
        "Créer un client",
        "Tous les clients",
        "Clients actifs",
        "Clients désactivés",
        "Clients en ligne",
        "Déconnecter un client",
        "Gestion des abonnements",
        "Trafic et consommation",
        "Définir un quota",
        "Date d'expiration",
        "Renouveler un abonnement",
        "Gestion des protocoles",
        "Mode par défaut",
        "Gestion des serveurs",
        "Historique des connexions",
        "Notifications",
        "Sécurité administrateur",
        "Sauvegarde et restauration"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = dark
        window.navigationBarColor = dark
        buildInterface()
        showDashboard()
    }

    // ANIMATION LUMINEUSE INDIVIDUELLE
    private fun pulse(view: View, strength: Float = 0.025f) {
        pulseCounter++

        val delay = (pulseCounter % 7) * 115L

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1250L + (pulseCounter % 4) * 180L
            startDelay = delay
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener {
                val value = it.animatedValue as Float
                val factor = 1f - strength + (strength * value)

                view.alpha = 0.88f + (0.12f * value)
                view.scaleX = factor
                view.scaleY = factor
            }
        }

        animator.start()
    }

    private fun pulseStatus(view: View, strength: Float = 0.045f) {
        pulse(view, strength)
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(dark)
        }

        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 25, 14, 14)
            setBackgroundColor(Color.rgb(12, 29, 50))
            visibility = View.GONE
        }

        val menuTitle = makeText("MENU", 20f, cyan, true)
        pulse(menuTitle, 0.035f)
        drawer.addView(menuTitle)

        val aboutButton = makeButton("À propos")
        aboutButton.setOnClickListener {
            drawer.visibility = View.GONE
            showAbout()
        }
        drawer.addView(aboutButton)

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10, 8, 10, 8)
            setBackgroundColor(Color.rgb(12, 25, 43))
        }

        val menuButton = makeButton("☰")
        menuButton.setOnClickListener {
            drawer.visibility =
                if (drawer.visibility == View.VISIBLE) View.GONE
                else View.VISIBLE
        }

        val heading = makeText("XXX-CORE ADMIN", 17f, cyan, true)
        heading.gravity = Gravity.CENTER
        pulse(heading, 0.035f)

        val loginButton = makeButton("Connexion")
        loginButton.setOnClickListener {
            showLoginDialog()
        }

        toolbar.addView(menuButton)
        toolbar.addView(
            heading,
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        toolbar.addView(loginButton)

        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 20)
        }

        val scroll = ScrollView(this)
        scroll.addView(body)

        statusText = makeText(
            "Administrateur non connecté",
            13f,
            blue
        )
        statusText.setPadding(12, 8, 12, 8)
        pulse(statusText, 0.02f)

        main.addView(toolbar)
        main.addView(statusText)
        main.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        root.addView(
            drawer,
            LinearLayout.LayoutParams(220, -1)
        )
        root.addView(
            main,
            LinearLayout.LayoutParams(0, -1, 1f)
        )

        setContentView(root)
    }

    private fun makeText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    }

    private fun makeButton(value: String): Button {
        return Button(this).apply {
            text = value
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(18, 48, 78))
                cornerRadius = 16f
                setStroke(2, blue)
            }
            pulse(this, 0.018f)
        }
    }

    private fun makeCard(color: Int = blue): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 14)

            background = GradientDrawable().apply {
                setColor(Color.rgb(15, 31, 50))
                cornerRadius = 17f
                setStroke(2, color)
            }

            pulse(this, 0.018f)
        }
    }

    private fun showDashboard() {
        currentScreen = "dashboard"
        body.removeAllViews()

        val brand = makeText(
            "FRANKDEV BUILD",
            15f,
            cyan,
            true
        )
        pulse(brand, 0.04f)
        body.addView(brand)

        val title = makeText(
            "TABLEAU DE BORD",
            25f,
            Color.WHITE,
            true
        )
        pulse(title, 0.025f)
        body.addView(title)

        val subtitle = makeText(
            "Administration Cloudflare X-UI",
            13f,
            blue
        )
        pulse(subtitle, 0.025f)
        body.addView(subtitle)

        val grid = GridLayout(this).apply {
            columnCount = 2
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }

        for (option in options) {
            val button = makeButton(option)
            button.minHeight = 76

            button.setOnClickListener {
                handleOption(option)
            }

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(
                    GridLayout.UNDEFINED,
                    1f
                )
                setMargins(5, 7, 5, 7)
            }

            grid.addView(button, params)
        }

        body.addView(grid)

        val footer = makeText(
            "SECURITY BY FRANKDEV\n© 2026 FRANKDEV BUILD",
            12f,
            cyan
        )
        pulse(footer, 0.025f)
        body.addView(footer)
    }

    private fun handleOption(option: String) {
        when (option) {
            "Tableau de bord" -> showDashboard()
            "Créer un client" -> showCreateClientDialog()
            "Tous les clients" -> loadClients("all")
            "Clients actifs" -> loadClients("active")
            "Clients désactivés" -> loadClients("disabled")
            "Clients en ligne" -> loadOnlineClients(false)
            "Déconnecter un client" -> loadOnlineClients(true)
            else -> showMessage(
                option,
                "Cette fonction sera connectée au serveur Cloudflare dans une prochaine étape."
            )
        }
    }

    private fun showCreateClientDialog() {
        if (adminToken.isBlank()) {
            showMessage(
                "Connexion requise",
                "Connecte-toi comme administrateur avant de créer un client."
            )
            return
        }

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
        }

        val usernameInput = EditText(this).apply {
            hint = "Nom d'utilisateur (3 à 32 caractères)"
            setSingleLine(true)
            inputType = 1
        }

        val passwordInput = EditText(this).apply {
            hint = "Mot de passe (10 à 128 caractères)"
            setSingleLine(true)
            inputType = 129
        }

        form.addView(usernameInput)
        form.addView(passwordInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Créer un client")
            .setMessage(
                "Le compte sera enregistré sur le serveur Cloudflare."
            )
            .setView(form)
            .setPositiveButton("Créer", null)
            .setNegativeButton("Annuler", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val username = usernameInput.text
                    .toString()
                    .trim()
                    .lowercase()

                val password = passwordInput.text.toString()

                if (!Regex("^[a-z0-9_.-]{3,32}$").matches(username)) {
                    usernameInput.error =
                        "3 à 32 caractères : lettres, chiffres, . _ ou -"
                    return@setOnClickListener
                }

                if (password.length !in 10..128) {
                    passwordInput.error =
                        "Le mot de passe doit contenir 10 à 128 caractères."
                    return@setOnClickListener
                }

                dialog.dismiss()
                createClient(username, password)
            }
        }

        dialog.show()
    }

    private fun createClient(
        username: String,
        password: String
    ) {
        if (adminToken.isBlank()) {
            showMessage(
                "Connexion requise",
                "La session administrateur est absente."
            )
            return
        }

        Thread {
            var connection: HttpURLConnection? = null

            try {
                connection = URL(
                    "$workerUrl/admin/clients"
                ).openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doOutput = true

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $adminToken"
                )
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                val data = JSONObject()
                    .put("username", username)
                    .put("password", password)
                    .toString()

                connection.outputStream.use {
                    it.write(data.toByteArray(Charsets.UTF_8))
                }

                val response = readResponse(connection)
                val json = JSONObject(response)

                if (connection.responseCode !in 200..299) {
                    throw Exception(
                        json.optString(
                            "error",
                            "La création du client a échoué."
                        )
                    )
                }

                runOnUiThread {
                    showMessage(
                        "Client créé",
                        "Le compte $username a été créé avec succès."
                    )
                    loadClients("all")
                }

            } catch (e: Exception) {
                runOnUiThread {
                    showMessage(
                        "Création impossible",
                        e.message ?: "Erreur réseau."
                    )
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun showLoginDialog() {
        val input = EditText(this).apply {
            hint = "Secret administrateur"
            inputType = 129
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle("Connexion administrateur")
            .setView(input)
            .setPositiveButton("Se connecter") { _, _ ->
                val secret = input.text.toString()

                if (secret.isNotBlank()) {
                    login(secret)
                } else {
                    showMessage("Erreur", "Secret manquant.")
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun login(secret: String) {
        Thread {
            var connection: HttpURLConnection? = null

            try {
                connection = URL(
                    "$workerUrl/admin/login"
                ).openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doOutput = true

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                val data = JSONObject()
                    .put("adminSecret", secret)
                    .toString()

                connection.outputStream.use {
                    it.write(data.toByteArray(Charsets.UTF_8))
                }

                val response = readResponse(connection)
                val json = JSONObject(response)

                if (
                    connection.responseCode in 200..299 &&
                    json.has("token")
                ) {
                    adminToken = json.getString("token")

                    runOnUiThread {
                        statusText.text =
                            "Administrateur connecté"
                        statusText.setTextColor(green)

                        showMessage(
                            "Connexion réussie",
                            "Bienvenue dans le panneau FRANKDEV."
                        )
                    }
                } else {
                    runOnUiThread {
                        showMessage(
                            "Connexion refusée",
                            json.optString(
                                "error",
                                "Vérifie ton secret."
                            )
                        )
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    showMessage(
                        "Erreur réseau",
                        e.message ?: "Connexion impossible."
                    )
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun loadClients(filter: String) {
        if (adminToken.isBlank()) {
            showMessage(
                "Connexion requise",
                "Connecte-toi comme administrateur."
            )
            return
        }

        Thread {
            var connection: HttpURLConnection? = null

            try {
                connection = URL(
                    "$workerUrl/admin/clients"
                ).openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $adminToken"
                )

                val response = readResponse(connection)
                val json = JSONObject(response)

                if (connection.responseCode !in 200..299) {
                    throw Exception(
                        json.optString("error", "Erreur serveur")
                    )
                }

                val clients =
                    json.optJSONArray("clients") ?: JSONArray()

                val selected = JSONArray()

                for (i in 0 until clients.length()) {
                    val client = clients.getJSONObject(i)
                    val active = client.optInt("active", 0) == 1

                    if (
                        filter == "all" ||
                        (filter == "active" && active) ||
                        (filter == "disabled" && !active)
                    ) {
                        selected.put(client)
                    }
                }

                runOnUiThread {
                    showClientList(selected)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    showMessage(
                        "Erreur",
                        e.message ?: "Impossible de charger les clients."
                    )
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun showClientList(clients: JSONArray) {
        currentScreen = "clients"
        body.removeAllViews()

        val title = makeText(
            "GESTION DES CLIENTS",
            22f,
            cyan,
            true
        )
        pulse(title, 0.035f)
        body.addView(title)

        val back = makeButton("← Retour au tableau de bord")
        back.setOnClickListener {
            showDashboard()
        }
        body.addView(back)

        if (clients.length() == 0) {
            body.addView(
                makeText("Aucun client trouvé.", 15f, Color.WHITE)
            )
        }

        for (i in 0 until clients.length()) {
            val client = clients.getJSONObject(i)

            val id = client.optString("id")
            val username = client.optString("username")
            val active = client.optInt("active", 0) == 1

            val stateColor = if (active) blue else red

            val card = makeCard(stateColor)

            val name = makeText(
                username,
                18f,
                Color.WHITE,
                true
            )
            pulse(name, 0.035f)
            card.addView(name)

            val status = makeText(
                if (active) "● ACTIF" else "● DÉSACTIVÉ",
                14f,
                stateColor,
                true
            )
            pulseStatus(status, 0.055f)
            card.addView(status)

            val toggle = makeButton(
                if (active) "Désactiver" else "Activer"
            )

            toggle.setOnClickListener {
                changeClientStatus(id, !active)
            }

            card.addView(toggle)

            val params = LinearLayout.LayoutParams(-1, -2)
            params.setMargins(0, 9, 0, 9)

            body.addView(card, params)
        }
    }

    private fun loadOnlineClients(disconnectMode: Boolean) {
        if (adminToken.isBlank()) {
            showMessage(
                "Connexion requise",
                "Connecte-toi comme administrateur."
            )
            return
        }

        Thread {
            var connection: HttpURLConnection? = null

            try {
                connection = URL(
                    "$workerUrl/admin/clients/online"
                ).openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $adminToken"
                )

                val response = readResponse(connection)
                val json = JSONObject(response)

                if (connection.responseCode !in 200..299) {
                    throw Exception(
                        json.optString(
                            "error",
                            "Impossible de récupérer les clients en ligne."
                        )
                    )
                }

                val clients =
                    json.optJSONArray("clients") ?: JSONArray()

                runOnUiThread {
                    showOnlineClients(clients, disconnectMode)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    showMessage(
                        "Erreur",
                        e.message ?: "Erreur de connexion au serveur."
                    )
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun showOnlineClients(
        clients: JSONArray,
        disconnectMode: Boolean
    ) {
        currentScreen = "online"
        body.removeAllViews()

        val title = makeText(
            if (disconnectMode) "DÉCONNECTER UN CLIENT"
            else "CLIENTS EN LIGNE",
            22f,
            cyan,
            true
        )
        pulse(title, 0.035f)
        body.addView(title)

        val count = makeText(
            "${clients.length()} session(s) détectée(s)",
            14f,
            green,
            true
        )
        pulseStatus(count)
        body.addView(count)

        val back = makeButton("← Retour au tableau de bord")
        back.setOnClickListener {
            showDashboard()
        }
        body.addView(back)

        val refresh = makeButton("↻ Actualiser")
        refresh.setOnClickListener {
            loadOnlineClients(disconnectMode)
        }
        body.addView(refresh)

        if (clients.length() == 0) {
            body.addView(
                makeText(
                    "Aucun client en ligne pour le moment.",
                    15f,
                    Color.WHITE
                )
            )
            return
        }

        for (i in 0 until clients.length()) {
            val client = clients.getJSONObject(i)

            val sessionId = client.optString("session_id")
            val username = client.optString("username")
            val connectedAt = client.optLong("connected_at", 0)
            val lastSeenAt = client.optLong("last_seen_at", 0)

            val card = makeCard(green)

            val name = makeText(
                username,
                19f,
                Color.WHITE,
                true
            )
            pulse(name, 0.035f)
            card.addView(name)

            val onlineStatus = makeText(
                "● EN LIGNE",
                14f,
                green,
                true
            )
            pulseStatus(onlineStatus, 0.065f)
            card.addView(onlineStatus)

            card.addView(
                makeText(
                    "Connecté depuis : ${formatTimestamp(connectedAt)}",
                    12f,
                    Color.LTGRAY
                )
            )

            card.addView(
                makeText(
                    "Dernière activité : ${formatTimestamp(lastSeenAt)}",
                    12f,
                    Color.LTGRAY
                )
            )

            if (disconnectMode) {
                val disconnectButton =
                    makeButton("Déconnecter ce client")

                disconnectButton.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Confirmer la déconnexion")
                        .setMessage(
                            "Voulez-vous révoquer la session de $username ?"
                        )
                        .setPositiveButton("Déconnecter") { _, _ ->
                            disconnectClient(sessionId, username)
                        }
                        .setNegativeButton("Annuler", null)
                        .show()
                }

                card.addView(disconnectButton)
            }

            val params = LinearLayout.LayoutParams(-1, -2)
            params.setMargins(0, 9, 0, 9)

            body.addView(card, params)
        }
    }

    private fun disconnectClient(
        sessionId: String,
        username: String
    ) {
        if (adminToken.isBlank()) {
            showMessage(
                "Connexion requise",
                "La session administrateur est absente."
            )
            return
        }

        if (
            !Regex(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
            ).matches(sessionId)
        ) {
            showMessage(
                "Erreur",
                "Identifiant de session invalide."
            )
            return
        }

        Thread {
            var connection: HttpURLConnection? = null

            try {
                connection = URL(
                    "$workerUrl/admin/clients/sessions/$sessionId/disconnect"
                ).openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $adminToken"
                )

                val response = readResponse(connection)
                val json = JSONObject(response)

                if (connection.responseCode !in 200..299) {
                    throw Exception(
                        json.optString(
                            "error",
                            "La déconnexion a échoué."
                        )
                    )
                }

                runOnUiThread {
                    showMessage(
                        "Session révoquée",
                        "La session de $username a été révoquée côté serveur."
                    )
                    loadOnlineClients(true)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    showMessage(
                        "Déconnexion impossible",
                        e.message ?: "Erreur réseau."
                    )
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0) return "Indisponible"

        return try {
            val formatter = java.text.SimpleDateFormat(
                "dd/MM/yyyy HH:mm:ss",
                java.util.Locale.getDefault()
            )
            formatter.format(java.util.Date(timestamp * 1000))
        } catch (_: Exception) {
            "Indisponible"
        }
    }

    private fun changeClientStatus(
        id: String,
        active: Boolean
    ) {
        Thread {
            var connection: HttpURLConnection? = null

            try {
                connection = URL(
                    "$workerUrl/admin/clients/$id/status"
                ).openConnection() as HttpURLConnection

                connection.requestMethod = "PATCH"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doOutput = true

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $adminToken"
                )
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                val data = JSONObject()
                    .put("active", active)
                    .toString()

                connection.outputStream.use {
                    it.write(data.toByteArray(Charsets.UTF_8))
                }

                val response = readResponse(connection)
                val json = JSONObject(response)

                if (connection.responseCode !in 200..299) {
                    throw Exception(
                        json.optString(
                            "error",
                            "Modification refusée"
                        )
                    )
                }

                runOnUiThread {
                    showMessage(
                        "Statut modifié",
                        if (active) "Client activé."
                        else "Client désactivé."
                    )
                    loadClients("all")
                }

            } catch (e: Exception) {
                runOnUiThread {
                    showMessage(
                        "Erreur",
                        e.message ?: "Modification impossible."
                    )
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun readResponse(
        connection: HttpURLConnection
    ): String {
        val stream =
            if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

        return stream?.bufferedReader()?.use {
            it.readText()
        } ?: "{}"
    }

    private fun showAbout() {
        currentScreen = "about"
        body.removeAllViews()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20, 28, 20, 28)

            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(5, 18, 38),
                    Color.rgb(10, 48, 83),
                    Color.rgb(3, 12, 27)
                )
            ).apply {
                cornerRadius = 22f
                setStroke(2, cyan)
            }

            pulse(this, 0.025f)
        }

        val appIcon = ImageView(this).apply {
            setImageDrawable(
                packageManager.getApplicationIcon(packageName)
            )
            layoutParams =
                LinearLayout.LayoutParams(100, 100)
            pulse(this, 0.035f)
        }

        panel.addView(appIcon)

        val appTitle = makeText(
            "XXX-CORE ADMIN",
            25f,
            Color.WHITE,
            true
        )
        pulse(appTitle, 0.04f)
        panel.addView(appTitle)

        val signature = makeText(
            "SIGNATURE FRANKDEV BUILD",
            15f,
            cyan,
            true
        )
        pulse(signature, 0.04f)
        panel.addView(signature)

        val version = try {
            packageManager.getPackageInfo(
                packageName,
                0
            ).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }

        panel.addView(
            makeText(
                "Version de l'application : $version",
                14f,
                Color.WHITE
            )
        )

        panel.addView(makeText(" ", 10f, Color.WHITE))

        addLink(
            panel,
            "Communauté WhatsApp",
            "https://chat.whatsapp.com/H6WfMk5m5PO1tBMuJifWMt?s=cl&p=a&iam=2"
        )

        addLink(
            panel,
            "Chaîne principale WhatsApp",
            "https://chat.whatsapp.com/CklD1bALdPN4QztR3jpUqS?s=cl&p=a&iam=2"
        )

        val copyright = makeText(
            "© 2026 FRANKDEV BUILD\nTous droits réservés",
            12f,
            cyan,
            true
        )
        pulse(copyright, 0.035f)
        panel.addView(copyright)

        body.addView(panel)

        val back = makeButton("← Retour au tableau de bord")
        back.setOnClickListener {
            showDashboard()
        }
        body.addView(back)
    }

    private fun addLink(
        parent: LinearLayout,
        label: String,
        url: String
    ) {
        val link = makeButton("↗ $label")

        link.setOnClickListener {
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(url)
                    )
                )
            } catch (_: Exception) {
                showMessage(
                    "Erreur",
                    "Impossible d'ouvrir ce lien."
                )
            }
        }

        parent.addView(link)
    }

    private fun showMessage(
        title: String,
        message: String
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (currentScreen != "dashboard") {
            showDashboard()
        } else {
            super.onBackPressed()
        }
    }
}
