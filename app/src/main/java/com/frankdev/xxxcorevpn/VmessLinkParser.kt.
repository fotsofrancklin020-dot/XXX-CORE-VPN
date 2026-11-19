
package com.frankdev.xxxcorevpn

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

data class VmessConfig(
    val name: String,
    val server: String,
    val port: Int,
    val uuid: String,
    val alterId: Int,
    val security: String,
    val transport: String,
    val rawHttp: Boolean,
    val path: String,
    val host: String,
    val tlsEnabled: Boolean,
    val serverName: String,
    val alpn: List<String>,
    val allowInsecure: Boolean,
    val fingerprint: String,
    val cipherSuites: List<String>,
    val globalPadding: Boolean,
    val authenticatedLength: Boolean,
    val packetEncoding: String
)

object VmessLinkParser {

    private val allowedSecurity = setOf(
        "auto", "none", "zero",
        "aes-128-gcm", "chacha20-poly1305",
        "aes-128-cfb"
    )

    private val allowedFingerprints = setOf(
        "chrome_psk", "chrome_psk_shuffle",
        "chrome_padding_psk_shuffle",
        "chrome_pq", "chrome_pq_psk",
        "chrome", "firefox", "edge", "safari",
        "360", "qq", "ios", "android",
        "random", "randomized"
    )

    private val uuidPattern = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-" +
        "[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
        "[0-9a-fA-F]{12}$"
    )

    fun parse(link: String): VmessConfig {
        require(link.trim().startsWith("vmess://", true)) {
            "Ce lien n'est pas un lien VMess."
        }

        val payload = link.trim()
            .substringAfter("://")
            .substringBefore("#")
            .trim()

        require(payload.isNotBlank()) {
            "Le lien VMess est vide."
        }

        val json = try {
            JSONObject(decodeBase64(payload))
        } catch (_: Exception) {
            throw IllegalArgumentException(
                "Impossible de lire les données VMess."
            )
        }

        val server = json.value("add").trim()
        val uuid = json.value("id").trim()
        val port = json.value("port").toIntOrNull()
            ?: throw IllegalArgumentException("Port invalide.")

        require(server.isNotBlank()) {
            "Adresse du serveur manquante."
        }
        require(!server.any { it.isWhitespace() } &&
                !server.contains("://")) {
            "Adresse du serveur invalide."
        }
        require(port in 1..65535) {
            "Port hors limites."
        }
        require(uuidPattern.matches(uuid)) {
            "UUID VMess invalide."
        }

        val alterId = json.value("aid")
            .ifBlank { "0" }
            .toIntOrNull()
            ?: throw IllegalArgumentException("Alter ID invalide.")

        require(alterId >= 0) {
            "Alter ID invalide."
        }

        val security = json.value("scy")
            .trim()
            .lowercase()
            .ifBlank { "auto" }

        require(security in allowedSecurity) {
            "Sécurité non prise en charge : $security"
        }

        val network = json.value("net").trim().lowercase()
        val rawType = json.value("type").trim().lowercase()

        val rawHttp =
            (network.isBlank() ||
             network == "tcp" ||
             network == "raw") &&
             rawType == "http"

        val transport = when (network) {
            "", "tcp", "raw" -> if (rawHttp) "http" else "tcp"
            "ws", "websocket" -> "ws"
            "h2", "http" -> "http"
            "grpc" -> "grpc"
            "quic" -> "quic"
            "httpupgrade" -> "httpupgrade"
            else -> throw IllegalArgumentException(
                "Transport non pris en charge : $network"
            )
        }

        var path = json.value("path")
        val host = json.value("host")

        if (rawHttp) {
            path = path.split(",")
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
        }

        val tlsValue = json.value("tls")
            .trim()
            .lowercase()

        require(
            tlsValue.isBlank() ||
            tlsValue == "tls" ||
            tlsValue == "none"
        ) {
            "Mode TLS non pris en charge : $tlsValue"
        }

        val tlsEnabled = tlsValue == "tls"

        val insecureValue = if (json.has("insecure")) {
            json.value("insecure")
        } else {
            json.value("allowInsecure")
        }

        val allowInsecure = parseBoolean(
            insecureValue, "insecure"
        )

        val fingerprint = json.value("fp").trim()

        if (tlsEnabled && fingerprint.isNotBlank()) {
            require(fingerprint in allowedFingerprints) {
                "Empreinte TLS non prise en charge : $fingerprint"
            }
        }

        val alpn = json.value("alpn")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val cipherSuites = json.value("cs")
            .split(":")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val extras = parseExtra(
            json.value("throneExtra")
        )

        return VmessConfig(
            name = json.value("ps"),
            server = server,
            port = port,
            uuid = uuid,
            alterId = alterId,
            security = security,
            transport = transport,
            rawHttp = rawHttp,
            path = path,
            host = host,
            tlsEnabled = tlsEnabled,
            serverName = json.value("sni").trim(),
            alpn = alpn,
            allowInsecure = allowInsecure,
            fingerprint = if (tlsEnabled) fingerprint else "",
            cipherSuites = if (tlsEnabled) cipherSuites else emptyList(),
            globalPadding = extras.first,
            authenticatedLength = extras.second,
            packetEncoding = extras.third
        )
    }

    fun toSingBoxOutboundJson(
        config: VmessConfig,
        tag: String = "vmess-proxy"
    ): JSONObject {
        require(tag.isNotBlank()) {
            "Le tag sing-box est obligatoire."
        }

        val outbound = JSONObject()
            .put("type", "vmess")
            .put("tag", tag)
            .put("server", config.server)
            .put("server_port", config.port)
            .put("uuid", config.uuid)

        if (config.security != "auto") {
            outbound.put("security", config.security)
        }

        if (config.alterId > 0) {
            outbound.put("alter_id", config.alterId)
        }

        if (config.globalPadding) {
            outbound.put("global_padding", true)
        }

        if (config.authenticatedLength) {
            outbound.put("authenticated_length", true)
        }

        if (config.packetEncoding.isNotBlank()) {
            outbound.put(
                "packet_encoding",
                config.packetEncoding
            )
        }

        if (config.tlsEnabled) {
            val tls = JSONObject()
                .put("enabled", true)

            if (config.serverName.isNotBlank()) {
                tls.put("server_name", config.serverName)
            }

            if (config.allowInsecure) {
                tls.put("insecure", true)
            }

            if (config.alpn.isNotEmpty()) {
                tls.put("alpn", JSONArray(config.alpn))
            }

            if (config.cipherSuites.isNotEmpty()) {
                tls.put(
                    "cipher_suites",
                    JSONArray(config.cipherSuites)
                )
            }

            if (config.fingerprint.isNotBlank()) {
                tls.put(
                    "utls",
                    JSONObject()
                        .put("enabled", true)
                        .put("fingerprint", config.fingerprint)
                )
            }

            outbound.put("tls", tls)
        }

        val transport = buildTransport(config)

        if (transport != null) {
            outbound.put("transport", transport)
        }

        return outbound
    }

    private fun buildTransport(
        config: VmessConfig
    ): JSONObject? {
        if (config.transport == "tcp") return null

        val result = JSONObject()
            .put("type", config.transport)

        when (config.transport) {
            "ws" -> {
                if (config.path.isNotBlank()) {
                    result.put("path", config.path)
                }

                if (config.host.isNotBlank()) {
                    result.put(
                        "headers",
                        JSONObject().put("Host", config.host)
                    )
                }
            }

            "http" -> {
                if (config.host.isNotBlank()) {
                    val hosts = config.host.split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    if (hosts.isNotEmpty()) {
                        result.put("host", JSONArray(hosts))
                    }
                }

                if (config.path.isNotBlank()) {
                    result.put("path", config.path)
                }

                if (config.rawHttp) {
                    result.put("method", "GET")
                }
            }

            "grpc" -> {
                if (config.path.isNotBlank()) {
                    result.put("service_name", config.path)
                }
            }

            "httpupgrade" -> {
                if (config.host.isNotBlank()) {
                    result.put("host", config.host)
                }

                if (config.path.isNotBlank()) {
                    result.put("path", config.path)
                }
            }

            "quic" -> Unit

            else -> throw IllegalArgumentException(
                "Transport inconnu : ${config.transport}"
            )
        }

        return result
    }

    private fun parseExtra(
        raw: String
    ): Triple<Boolean, Boolean, String> {
        if (raw.isBlank()) {
            return Triple(false, false, "")
        }

        val uri = Uri.parse(
            "https://localhost/?$raw"
        )

        val allowed = setOf(
            "globalPadding",
            "authenticatedLength",
            "packetEncoding"
        )

        val unsupported = uri.queryParameterNames - allowed

        require(unsupported.isEmpty()) {
            "Options avancées non prises en charge : " +
                unsupported.joinToString(", ")
        }

        val padding = parseBoolean(
            uri.getQueryParameter("globalPadding").orEmpty(),
            "globalPadding"
        )

        val authenticated = parseBoolean(
            uri.getQueryParameter("authenticatedLength").orEmpty(),
            "authenticatedLength"
        )

        val encoding = uri
            .getQueryParameter("packetEncoding")
            .orEmpty()
            .trim()
            .lowercase()

        require(
            encoding in setOf("", "none", "packetaddr", "xudp")
        ) {
            "Encodage de paquets inconnu : $encoding"
        }

        return Triple(
            padding,
            authenticated,
            if (encoding == "none") "" else encoding
        )
    }

    private fun parseBoolean(
        value: String,
        field: String
    ): Boolean {
        return when (value.trim().lowercase()) {
            "", "0", "false" -> false
            "1", "true" -> true
            else -> throw IllegalArgumentException(
                "Valeur invalide pour $field."
            )
        }
    }

    private fun decodeBase64(payload: String): String {
        val normalized = payload
            .replace("\\s".toRegex(), "")
            .replace('-', '+')
            .replace('_', '/')

        val padded = normalized +
            "=".repeat((4 - normalized.length % 4) % 4)

        val decoded = try {
            Base64.decode(padded, Base64.DEFAULT)
        } catch (_: Exception) {
            throw IllegalArgumentException(
                "Décodage Base64 impossible."
            )
        }

        return decoded.toString(Charsets.UTF_8)
    }

    private fun JSONObject.value(key: String): String {
        if (!has(key) || isNull(key)) return ""
        return opt(key)?.toString().orEmpty()
    }
}
