
package com.frankdev.xxxcorevpn

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ClientApi {

    fun login(
        context: Context,
        baseUrl: String,
        username: String,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        Thread {
            var connection: HttpURLConnection? = null

            try {
                val cleanUrl = baseUrl.trim().trimEnd('/')

                require(cleanUrl.startsWith("https://")) {
                    "L'API doit utiliser HTTPS."
                }

                connection = URL("$cleanUrl/client/login")
                    .openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )
                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                val requestBody = JSONObject().apply {
                    put("username", username.trim())
                    put("password", password)
                }

                connection.outputStream.use { output ->
                    output.write(
                        requestBody.toString().toByteArray(Charsets.UTF_8)
                    )
                }

                val responseCode = connection.responseCode

                val responseStream =
                    if (responseCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                val responseText = responseStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?: ""

                val response = JSONObject(responseText)

                if (responseCode in 200..299) {
                    val token = response.optString("token")

                    require(token.matches(Regex("[a-f0-9]{64}"))) {
                        "Jeton de session invalide."
                    }

                    ClientSessionStore.saveToken(context, token)

                    Handler(Looper.getMainLooper()).post {
                        onResult(
                            true,
                            "Connexion réussie."
                        )
                    }
                } else {
                    val error = response.optString(
                        "message",
                        response.optString(
                            "error",
                            "Échec de la connexion."
                        )
                    )

                    Handler(Looper.getMainLooper()).post {
                        onResult(false, error)
                    }
                }

            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onResult(
                        false,
                        e.message ?: "Erreur de connexion à l'API."
                    )
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }
}
