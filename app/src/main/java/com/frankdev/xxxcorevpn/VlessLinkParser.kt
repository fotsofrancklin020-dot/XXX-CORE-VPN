
package com.frankdev.xxxcorevpn

import android.net.Uri
import org.json.JSONObject
import java.util.UUID

object VlessLinkParser {

    fun parse(link: String): JSONObject {
        val value = link.trim()

        require(value.startsWith("vless://", true)) {
            "Ce lien n'est pas un lien VLESS."
        }

        val uri = Uri.parse(value)

        val uuid = uri.userInfo.orEmpty().substringBefore(":").trim()
        val server = uri.host.orEmpty().trim()
        val port = uri.port

        require(server.isNotBlank()) {
            "Adresse du serveur VLESS manquante."
        }

        require(port in 1..65535) {
            "Port VLESS invalide."
        }

        try {
            UUID.fromString(uuid)
        } catch (_: Exception) {
            throw IllegalArgumentException("UUID VLESS invalide.")
        }

        val encryption = uri.getQueryParameter("encryption")
            .orEmpty().ifBlank { "none" }

        require(encryption.equals("none", true)) {
            "Chiffrement VLESS non pris en charge : $encryption"
        }

        val outbound = JSONObject()
            .put("type", "vless")
            .put("tag", "vless-proxy")
            .put("server", server)
            .put("server_port", port)
            .put("uuid", uuid)

        val flow = uri.getQueryParameter("flow").orEmpty().trim()

        if (flow.isNotBlank()) {
            require(flow == "xtls-rprx-vision") {
                "Flow VLESS non pris en charge : $flow"
            }
            outbound.put("flow", flow)
        }

        val security = uri.getQueryParameter("security")
            .orEmpty().lowercase().ifBlank { "none" }

        when (security) {
            "none" -> Unit

            "tls", "reality" -> {
                val tls = JSONObject().put("enabled", true)

                val sni = uri.getQueryParameter("sni")
                    ?: uri.getQueryParameter("servername")
                    ?: ""

                if (sni.isNotBlank()) {
                    tls.put("server_name", sni)
                }

                val insecure = uri.getQueryParameter("insecure")
                    ?: uri.getQueryParameter("allowInsecure")
                    ?: "false"

                if (insecure.equals("true", true) || insecure == "1") {
                    tls.put("insecure", true)
                }

                val alpn = uri.getQueryParameter("alpn")
                    .orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                if (alpn.isNotEmpty()) {
                    tls.put("alpn", org.json.JSONArray(alpn))
                }

                val fingerprint = uri.getQueryParameter("fp")
                    .orEmpty().trim()

                if (fingerprint.isNotBlank()) {
                    tls.put(
                        "utls",
                        JSONObject()
                            .put("enabled", true)
                            .put("fingerprint", fingerprint)
                    )
                }

                if (security == "reality") {
                    val publicKey = uri.getQueryParameter("pbk")
                        .orEmpty().trim()

                    val shortId = uri.getQueryParameter("sid")
                        .orEmpty().trim()

                    require(publicKey.isNotBlank()) {
                        "Clé publique Reality (pbk) manquante."
                    }

                    val reality = JSONObject()
                        .put("enabled", true)
                        .put("public_key", publicKey)

                    if (shortId.isNotBlank()) {
                        reality.put("short_id", shortId)
                    }

                    tls.put("reality", reality)
                }

                outbound.put("tls", tls)
            }

            else -> throw IllegalArgumentException(
                "Sécurité VLESS non prise en charge : $security"
            )
        }

        val transport = uri.getQueryParameter("type")
            .orEmpty().lowercase().ifBlank { "tcp" }

        when (transport) {
            "tcp", "raw" -> Unit

            "ws" -> {
                val ws = JSONObject()
                    .put(
                        "type",
                        "ws"
                    )

                val path = uri.getQueryParameter("path").orEmpty()
                val host = uri.getQueryParameter("host").orEmpty()

                if (path.isNotBlank()) {
                    ws.put("path", path)
                }

                if (host.isNotBlank()) {
                    ws.put(
                        "headers",
                        JSONObject().put("Host", host)
                    )
                }

                outbound.put("transport", ws)
            }

            "grpc" -> {
                val serviceName = uri.getQueryParameter("serviceName")
                    ?: uri.getQueryParameter("service_name")
                    ?: ""

                outbound.put(
                    "transport",
                    JSONObject()
                        .put("type", "grpc")
                        .put("service_name", serviceName)
                )
            }

            "httpupgrade" -> {
                val upgrade = JSONObject().put("type", "httpupgrade")

                val path = uri.getQueryParameter("path").orEmpty()
                val host = uri.getQueryParameter("host").orEmpty()

                if (path.isNotBlank()) {
                    upgrade.put("path", path)
                }

                if (host.isNotBlank()) {
                    upgrade.put(
                        "headers",
                        JSONObject().put("Host", host)
                    )
                }

                outbound.put("transport", upgrade)
            }

            else -> throw IllegalArgumentException(
                "Transport VLESS non pris en charge : $transport"
            )
        }

        return outbound
    }

    fun getName(link: String): String {
        return Uri.decode(link.substringAfter("#", ""))
            .trim()
    }
}
