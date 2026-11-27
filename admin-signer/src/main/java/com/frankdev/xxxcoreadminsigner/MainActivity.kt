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
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
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

    @Volatile
    private var adminToken = ""

    @Volatile
    private var adminExpiresAt: Long = 0L

    private var authButton: Button? = null
    @Volatile
    private var verificationInProgress = false

    private lateinit var drawer: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var toolbar: LinearLayout

    private var currentScreen = "dashboard"
    private var pulseCounter = 0
private val pulseAnimators = java.util.WeakHashMap<View, ValueAnimator>()

private fun stopPulses(view: View) {
    pulseAnimators.remove(view)?.cancel()

    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            stopPulses(view.getChildAt(i))
        }
    }
}
    private var loginSubmitButton: Button? = null

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

    override fun onResume() {
        super.onResume()

        if (adminToken.isNotBlank() && hasActiveAdminSession()) {
            verifyAdminSession(adminToken)
        } else {
            refreshAuthUi()
        }
    }

    // ANIMATION LUMINEUSE INDIVIDUELLE
private fun pulse(view: View, strength: Float = 0.025f) {
    pulseAnimators.remove(view)?.cancel()

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

    pulseAnimators[view] = animator
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

        toolbar = LinearLayout(this).apply {
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
        authButton = loginButton
        loginButton.setOnClickListener {
            if (hasActiveAdminSession()) {
                logoutAdmin()
            } else {
                showLoginScreen()
            }
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

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(body)
        }

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
        loginSubmitButton = null

        toolbar.visibility = View.VISIBLE
        statusText.visibility = View.VISIBLE
        drawer.visibility = View.GONE

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )

        stopPulses(body)
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

        refreshAuthUi()
    }

    private fun handleOption(option: String) {
        if (
            option != "Tableau de bord" &&
            !requireActiveAdminSession()
        ) {
            return
        }

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
        if (!requireActiveAdminSession()) return
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
        if (!requireActiveAdminSession()) return
        val requestToken = adminToken

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
                    "Bearer $requestToken"
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

    
    // PAGE DE CONNEXION ADMINISTRATEUR
    private fun showLoginScreen() {
        currentScreen = "login"
        drawer.visibility = View.GONE
        toolbar.visibility = View.GONE
        statusText.visibility = View.GONE
        loginSubmitButton = null

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        stopPulses(body)
        body.removeAllViews()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(18, 28, 18, 28)
            minimumHeight = resources.displayMetrics.heightPixels
        }

        val brand = makeText(
            "FRANKDEV BUILD",
            17f,
            cyan,
            true
        )
        pulse(brand, 0.045f)
        page.addView(brand)

        val shield = makeText(
            "✦",
            58f,
            blue,
            true
        )
        pulse(shield, 0.055f)

        val shieldParams = LinearLayout.LayoutParams(-1, -2)
        shieldParams.setMargins(0, 14, 0, 8)
        page.addView(shield, shieldParams)

        val title = makeText(
            "ACCÈS ADMINISTRATEUR",
            23f,
            Color.WHITE,
            true
        )
        pulse(title, 0.035f)

        val titleParams = LinearLayout.LayoutParams(-1, -2)
        titleParams.setMargins(0, 8, 0, 6)
        page.addView(title, titleParams)

        val subtitle = makeText(
            "CLOUDFLARE X-UI PANEL",
            13f,
            blue,
            true
        )
        page.addView(subtitle)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20, 24, 20, 22)

            background = GradientDrawable().apply {
                setColor(Color.rgb(12, 27, 46))
                cornerRadius = 24f
                setStroke(2, blue)
            }

            pulse(this, 0.018f)
        }

        val cardTitle = makeText(
            "Authentification sécurisée",
            18f,
            cyan,
            true
        )

        val cardTitleParams = LinearLayout.LayoutParams(-1, -2)
        cardTitleParams.setMargins(0, 0, 0, 12)
        card.addView(cardTitle, cardTitleParams)

        val explanation = makeText(
            "Saisis ton secret administrateur pour accéder au panneau de contrôle.",
            13f,
            grey
        )
        explanation.setPadding(0, 0, 0, 18)
        card.addView(explanation)

        val secretInput = EditText(this).apply {
            hint = "Secret administrateur"
            setSingleLine(true)
            inputType = 129
            imeOptions = EditorInfo.IME_ACTION_DONE
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(grey)
            setPadding(16, 14, 16, 14)

            background = GradientDrawable().apply {
                setColor(Color.rgb(5, 16, 30))
                cornerRadius = 14f
                setStroke(2, blue)
            }
        }

        val inputParams = LinearLayout.LayoutParams(-1, -2)
        inputParams.setMargins(0, 6, 0, 16)
        card.addView(secretInput, inputParams)

        val submit = makeButton("SE CONNECTER")
        submit.textSize = 15f
        submit.minHeight = 56

        val submitParams = LinearLayout.LayoutParams(-1, -2)
        submitParams.setMargins(0, 4, 0, 10)
        card.addView(submit, submitParams)

        loginSubmitButton = submit

        submit.setOnClickListener {
            val secret = secretInput.text.toString()

            if (secret.isBlank()) {
                secretInput.error = "Le secret administrateur est obligatoire."
                return@setOnClickListener
            }

            submit.isEnabled = false
            submit.text = "CONNEXION EN COURS..."

            login(secret)
        }

        secretInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit.performClick()
                true
            } else {
                false
            }
        }

        val securityNote = makeText(
            "🔒 Accès réservé à l'administrateur FRANKDEV",
            12f,
            green,
            true
        )
        securityNote.setPadding(0, 12, 0, 0)
        card.addView(securityNote)

        val cardParams = LinearLayout.LayoutParams(-1, -2)
        cardParams.setMargins(0, 25, 0, 18)
        page.addView(card, cardParams)

        val back = makeButton("← RETOUR")
        back.setOnClickListener {
            showDashboard()
        }

        val backParams = LinearLayout.LayoutParams(-1, -2)
        backParams.setMargins(0, 8, 0, 8)
        page.addView(back, backParams)

        val footer = makeText(
            "SECURITY BY FRANKDEV\n© 2026 FRANKDEV BUILD",
            11f,
            cyan
        )
        pulse(footer, 0.025f)

        val footerParams = LinearLayout.LayoutParams(-1, -2)
        footerParams.setMargins(0, 18, 0, 0)
        page.addView(footer, footerParams)

        body.addView(
            page,
            LinearLayout.LayoutParams(-1, -1)
        )
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

                val receivedToken = json.optString("token", "")
                val receivedExpiry = json.optLong("expires_at", 0L)
                val nowSeconds = System.currentTimeMillis() / 1000L

                if (
                    connection.responseCode in 200..299 &&
                    receivedToken.isNotBlank() &&
                    receivedExpiry > nowSeconds
                ) {
                    adminToken = receivedToken
                    adminExpiresAt = receivedExpiry

                    runOnUiThread {
                        loginSubmitButton = null
                        showDashboard()
                        refreshAuthUi()

                        showMessage(
                            "Connexion réussie",
                            "Bienvenue dans le panneau FRANKDEV. La session expirera automatiquement."
                        )

                        verifyAdminSession(receivedToken)
                    }
                } else {
                    runOnUiThread {
                        loginSubmitButton?.apply {
                            isEnabled = true
                            text = "SE CONNECTER"
                        }

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
                    loginSubmitButton?.apply {
                        isEnabled = true
                        text = "SE CONNECTER"
                    }

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

    private fun hasActiveAdminSession(): Boolean {
        if (adminToken.isBlank()) return false

        val nowSeconds = System.currentTimeMillis() / 1000L
        if (adminExpiresAt <= nowSeconds) {
            clearAdminSession()
            return false
        }

        return true
    }

    private fun requireActiveAdminSession(): Boolean {
        if (hasActiveAdminSession()) return true

        refreshAuthUi()
        showMessage(
            "Connexion requise",
            "La session administrateur est absente ou expirée. Reconnecte-toi."
        )

        if (currentScreen != "login") {
            showLoginScreen()
        }

        return false
    }

    private fun refreshAuthUi() {
        val active =
            adminToken.isNotBlank() &&
            adminExpiresAt > System.currentTimeMillis() / 1000L

        authButton?.text = if (active) "Déconnexion" else "Connexion"

        if (::statusText.isInitialized && currentScreen == "dashboard") {
            statusText.text = if (active) {
                "Administrateur connecté"
            } else {
                "Administrateur non connecté"
            }
            statusText.setTextColor(if (active) green else blue)
        }
    }

    private fun clearAdminSession() {
        adminToken = ""
        adminExpiresAt = 0L
        refreshAuthUi()
    }

    private fun verifyAdminSession(token: String) {
        if (verificationInProgress) return
        verificationInProgress = true

        Thread {
            var connection: HttpURLConnection? = null

            try {
                connection = URL("$workerUrl/admin/me")
                    .openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $token"
                )

                val code = connection.responseCode
                readResponse(connection)

                runOnUiThread {
                    if (adminToken == token) {
                        if (code == 401 || code == 403) {
                            clearAdminSession()
                            if (currentScreen != "dashboard") {
                                showDashboard()
                            }
                            showMessage(
                                "Session expirée",
                                "Le serveur a refusé cette session. Reconnecte-toi."
                            )
                        } else if (code in 200..299) {
                            refreshAuthUi()
                        }
                    }
                }
            } catch (_: Exception) {
                // Une panne réseau temporaire ne détruit pas la session locale.
            } finally {
                connection?.disconnect()
                verificationInProgress = false
            }
        }.start()
    }

    private fun logoutAdmin() {
        if (!hasActiveAdminSession()) {
            showDashboard()
            return
        }

        val token = adminToken
        authButton?.isEnabled = false

        Thread {
            var connection: HttpURLConnection? = null

            try {
                connection = URL("$workerUrl/admin/logout")
                    .openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doOutput = true

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $token"
                )
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                connection.outputStream.use {
                    it.write("{}".toByteArray(Charsets.UTF_8))
                }

                val response = readResponse(connection)
                val code = connection.responseCode

                runOnUiThread {
                    authButton?.isEnabled = true

                    if (code in 200..299) {
                        if (adminToken == token) {
                            clearAdminSession()
                        }

                        showDashboard()
                        showMessage(
                            "Déconnexion réussie",
                            "La session administrateur a été révoquée côté serveur."
                        )
                    } else {
                        val errorMessage = try {
                            JSONObject(response).optString(
                                "error",
                                "Le serveur n'a pas confirmé la révocation."
                            )
                        } catch (_: Exception) {
                            "Le serveur n'a pas confirmé la révocation."
                        }

                        showMessage(
                            "Déconnexion non confirmée",
                            errorMessage
                        )
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    authButton?.isEnabled = true
                    showMessage(
                        "Erreur réseau",
                        "La révocation côté serveur n'a pas pu être confirmée. ${e.message ?: ""}"
                    )
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun loadClients(filter: String) {
        if (!requireActiveAdminSession()) return
        val requestToken = adminToken

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
                    "Bearer $requestToken"
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
    stopPulses(body)
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
        return
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

        val statusIcon = makeText(
            if (active) "✓" else "⛔",
            30f,
            stateColor,
            true
        )
        pulseStatus(statusIcon, 0.075f)
        card.addView(statusIcon)

        val statusText = makeText(
            if (active) "ACTIF" else "DÉSACTIVÉ",
            15f,
            stateColor,
            true
        )
        pulseStatus(statusText, 0.055f)
        card.addView(statusText)

        val toggle = makeButton(
            if (active) "🔴 Désactiver le client"
            else "🟢 Activer le client"
        )

        toggle.setOnClickListener {
            changeClientStatus(id, !active)
        }
        card.addView(toggle)

        val deleteButton = makeButton(
            "🗑 Supprimer définitivement"
        )

        deleteButton.setTextColor(Color.WHITE)
        deleteButton.background = GradientDrawable().apply {
            setColor(Color.rgb(105, 20, 32))
            cornerRadius = 16f
            setStroke(2, red)
        }

        deleteButton.setOnClickListener {
            if (!Regex(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
            ).matches(id)) {
                showMessage(
                    "Erreur",
                    "Identifiant du client invalide."
                )
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("⚠️ Suppression définitive")
                .setMessage(
                    "Voulez-vous vraiment supprimer le client $username ?\n\n" +
                    "Cette opération supprimera également ses sessions et ses configurations. Elle est irréversible."
                )
                .setPositiveButton("Supprimer") { _, _ ->
                    deleteClient(id, username)
                }
                .setNegativeButton("Annuler", null)
                .show()
        }

        card.addView(deleteButton)

        val params = LinearLayout.LayoutParams(-1, -2)
        params.setMargins(0, 9, 0, 9)

        body.addView(card, params)
    }
}
    private fun loadOnlineClients(disconnectMode: Boolean) {
        if (!requireActiveAdminSession()) return
        val requestToken = adminToken

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
                    "Bearer $requestToken"
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
    stopPulses(body)
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
        "🟢 ${clients.length()} SESSION(S) DÉTECTÉE(S)",
        14f,
        green,
        true
    )
    pulseStatus(count, 0.065f)
    body.addView(count)

    val back = makeButton("← Retour au tableau de bord")
    back.setOnClickListener {
        showDashboard()
    }
    body.addView(back)

    val refresh = makeButton("↻ Actualiser les connexions")
    refresh.setOnClickListener {
        loadOnlineClients(disconnectMode)
    }
    body.addView(refresh)

    if (clients.length() == 0) {
        val emptyIcon = makeText(
            "⚪",
            38f,
            grey,
            true
        )
        pulseStatus(emptyIcon)
        body.addView(emptyIcon)

        body.addView(
            makeText(
                "AUCUN CLIENT CONNECTÉ",
                15f,
                grey,
                true
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

        val onlineIcon = makeText(
            "📶",
            34f,
            green,
            true
        )
        pulseStatus(onlineIcon, 0.075f)
        card.addView(onlineIcon)

        val onlineStatus = makeText(
            "EN LIGNE",
            15f,
            green,
            true
        )
        pulseStatus(onlineStatus, 0.065f)
        card.addView(onlineStatus)

        card.addView(
            makeText(
                "🕒 Connecté depuis : ${formatTimestamp(connectedAt)}",
                12f,
                Color.LTGRAY
            )
        )

        card.addView(
            makeText(
                "⏱ Dernière activité : ${formatTimestamp(lastSeenAt)}",
                12f,
                Color.LTGRAY
            )
        )

        if (disconnectMode) {
            val disconnectButton =
                makeButton("🔴 Déconnecter ce client")

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
        if (!requireActiveAdminSession()) return
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

        val requestToken = adminToken

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
                    "Bearer $requestToken"
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
        if (!requireActiveAdminSession()) return

        val requestToken = adminToken

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
                    "Bearer $requestToken"
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
        stopPulses(body)
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
