
package com.frankdev.xxxcorevpn

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

data class MultiplexConfig(
    val specified: Boolean = false,
    val enabled: Boolean = false,
    val protocol: String = "",
    val maxConnections: Int = 0,
    val minStreams: Int = 0,
    val maxStreams: Int = 0,
    val padding: Boolean = false,
    val brutalEnabled: Boolean = false,
    val brutalUpMbps: Int = 0,
    val brutalDownMbps: Int = 0
)

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
    val packetEncoding: String,
    val serviceName: String = "",
    val transportMethod: String = "",
    val transportHeaders: Map<String, String> = emptyMap(),
    val idleTimeout: String = "",
    val pingTimeout: String = "",
    val maxEarlyData: Int = 0,
    val earlyDataHeaderName: String = "",
    val multiplex: MultiplexConfig = MultiplexConfig(),
    val disableSni: Boolean = false
)

object VmessLinkParser {

    private val allowedSecurity = setOf(
        "auto", "none", "zero",
        "aes-128-gcm", "chacha20-poly1305",
        "aes-128-cfb"
    )

    private val allowedFingerprints = setOf(
        "chrome", "chrome_psk", "chrome_psk_shuffle",
        "chrome_padding_psk_shuffle", "chrome_pq",
        "chrome_pq_psk", "firefox", "edge", "safari",
        "360", "qq", "ios", "android", "random",
        "randomized"
    )

    private val uuidPattern = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-" +
        "[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
        "[0-9a-fA-F]{12}$"
    )

    fun parse(link: String): VmessConfig {
        val value = link.trim()

        // Import direct du JSON Xray VMess.
        if (value.startsWith("{")) {
            val json = try {
                JSONObject(value)
            } catch (_: Exception) {
                throw IllegalArgumentException("JSON VMess invalide.")
            }
            return parseXrayConfig(json)
        }

        require(value.startsWith("vmess://", true)) {
            "Ce lien n'est pas un lien VMess."
        }

        val payload = value.substringAfter("://")
            .substringBefore("#")

        require(payload.isNotBlank()) {
            "Le lien VMess est vide."
        }

        val decoded = try {
            decodeBase64(payload)
        } catch (_: Exception) {
            ""
        }

        if (decoded.trimStart().startsWith("{")) {
            val json = try {
                JSONObject(decoded)
            } catch (_: Exception) {
                throw IllegalArgumentException(
                    "Les données VMess JSON sont invalides."
                )
            }
            return parseBase64Config(json, value)
        }

        return parseUrlConfig(value)
    }

    // Import JSON Xray VMess.
    private fun parseXrayConfig(json: JSONObject): VmessConfig {
        val protocol = json.value("protocol").lowercase()
        require(protocol.isBlank() || protocol == "vmess") {
            "Ce JSON ne contient pas un protocole VMess."
        }

        val settings = json.optJSONObject("settings")
            ?: throw IllegalArgumentException("Section settings absente.")

        val vnext = settings.optJSONArray("vnext")
            ?: throw IllegalArgumentException("Section vnext absente.")

        require(vnext.length() > 0) {
            "Aucun serveur VMess trouvé."
        }

        val node = vnext.getJSONObject(0)

        val users = node.optJSONArray("users")
            ?: throw IllegalArgumentException("Utilisateur VMess absent.")

        require(users.length() > 0) {
            "Aucun utilisateur VMess trouvé."
        }

        val user = users.getJSONObject(0)

        val server = node.value("address").trim()
        val port = node.value("port").toIntOrNull()
            ?: throw IllegalArgumentException("Port VMess invalide.")
        val uuid = user.value("id").trim()

        validateServer(server, port, uuid)

        val alterId = (user.value("alterId")
            .ifBlank { user.value("alter_id") }
            .ifBlank { "0" }).toIntOrNull()
            ?: throw IllegalArgumentException("Alter ID invalide.")

        require(alterId >= 0) {
            "Alter ID invalide."
        }

        val securityValue = user.value("security")
            .ifBlank { user.value("encryption") }

        val security = normalizeSecurity(securityValue)

        val stream = json.optJSONObject("streamSettings") ?: JSONObject()

        val network = stream.value("network")
            .ifBlank { "tcp" }

        val tlsMode = stream.value("security").lowercase()

        require(tlsMode != "reality") {
            "VMess Reality n'est pas encore pris en charge."
        }

        require(
            tlsMode.isBlank() ||
            tlsMode == "none" ||
            tlsMode == "tls"
        ) {
            "Sécurité de transport non prise en charge : $tlsMode"
        }

        val tlsEnabled = tlsMode == "tls"
        val tls = stream.optJSONObject("tlsSettings") ?: JSONObject()

        val alpnArray = tls.optJSONArray("alpn")
        val alpn = if (alpnArray != null) {
            (0 until alpnArray.length())
                .map { alpnArray.optString(it) }
                .filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        val fingerprint = tls.value("fingerprint")

        if (tlsEnabled && fingerprint.isNotBlank()) {
            require(fingerprint in allowedFingerprints) {
                "Empreinte TLS non prise en charge : $fingerprint"
            }
        }

        val insecure = if (tls.has("allowInsecure")) {
            tls.value("allowInsecure")
        } else {
            tls.value("allow_insecure")
        }

        val transportName = network.lowercase()
        val tcpSettings = stream.optJSONObject("tcpSettings")
            ?: JSONObject()
        val rawHeader = tcpSettings.optJSONObject("header")
            ?: JSONObject()

        val rawType = rawHeader.value("type").lowercase()

        val rawHttp =
            transportName in setOf("", "tcp", "raw") &&
            rawType == "http"

        val transport = normalizeTransport(transportName, rawHttp)

        var path = ""
        var host = ""
        var method = ""
        var headers = emptyMap<String, String>()
        var serviceName = ""

        when (transport) {
            "ws" -> {
                val ws = stream.optJSONObject("wsSettings")
                    ?: JSONObject()

                path = ws.value("path")

                val wsHeaders = ws.optJSONObject("headers")
                    ?: JSONObject()

                host = wsHeaders.value("Host").ifBlank {
                    wsHeaders.value("host")
                }

                headers = jsonHeaders(wsHeaders)
            }

            "http" -> {
                val http = stream.optJSONObject("httpSettings")
                    ?: JSONObject()

                path = http.value("path")

                val hosts = http.optJSONArray("host")

                host = if (hosts != null && hosts.length() > 0) {
                    (0 until hosts.length()).joinToString(",") {
                        hosts.optString(it)
                    }
                } else {
                    http.value("host")
                }

                method = http.value("method")

                headers = jsonHeaders(
                    http.optJSONObject("headers") ?: JSONObject()
                )

                if (rawHttp) {
                    val request = rawHeader.optJSONObject("request")
                        ?: JSONObject()

                    method = request.value("method").ifBlank {
                        "GET"
                    }

                    val paths = request.optJSONArray("path")

                    if (paths != null && paths.length() > 0) {
                        path = paths.optString(0)
                    }
                }
            }

            "grpc" -> {
                val grpc = stream.optJSONObject("grpcSettings")
                    ?: JSONObject()

                serviceName = grpc.value("serviceName")
            }

            "httpupgrade" -> {
                val upgrade = stream.optJSONObject("httpupgradeSettings")
                    ?: JSONObject()

                path = upgrade.value("path")

                val upgradeHeaders = upgrade.optJSONObject("headers")
                    ?: JSONObject()

                host = upgradeHeaders.value("Host").ifBlank {
                    upgradeHeaders.value("host")
                }

                headers = jsonHeaders(upgradeHeaders)
            }
        }

        return VmessConfig(
            name = json.value("tag").ifBlank {
                json.value("ps")
            },
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
            serverName = tls.value("serverName"),
            alpn = alpn,
            allowInsecure = parseBoolean(
                insecure,
                "allowInsecure"
            ),
            fingerprint = if (tlsEnabled) fingerprint else "",
            cipherSuites = emptyList(),
            globalPadding = false,
            authenticatedLength = false,
            packetEncoding = "",
            serviceName = serviceName,
            transportMethod = method,
            transportHeaders = headers
        )
    }

    private fun jsonHeaders(json: JSONObject): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val keys = json.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = json.value(key)
        }

        return result
    }

    private fun parseBase64Config(
        json: JSONObject,
        originalLink: String
    ): VmessConfig {
        val server = json.value("add").trim()
        val uuid = json.value("id").trim()
        val port = json.value("port").toIntOrNull()
            ?: throw IllegalArgumentException("Port VMess invalide.")

        validateServer(server, port, uuid)

        val alterId = json.value("aid")
            .ifBlank { "0" }.toIntOrNull()
            ?: throw IllegalArgumentException("Alter ID invalide.")

        require(alterId >= 0) { "Alter ID invalide." }

        val security = normalizeSecurity(json.value("scy"))

        val network = json.value("net").lowercase().trim()
        val rawType = json.value("type").lowercase().trim()

        val rawHttp =
            (network.isBlank() || network == "tcp" || network == "raw") &&
            rawType == "http"

        val baseTransport = normalizeTransport(network, rawHttp)
        var path = json.value("path")

        if (rawHttp) {
            path = path.split(",").map { it.trim() }
                .firstOrNull { it.isNotBlank() }.orEmpty()
        }

        val extras = parseExtra(json.value("throneExtra"))

        val transport = extras.transport.ifBlank {
            baseTransport
        }

        val finalPath = extras.path.ifBlank { path }
        val finalHost = extras.host.ifBlank {
            json.value("host")
        }

        val tlsValue = json.value("tls").lowercase().trim()

        require(
            tlsValue.isBlank() ||
            tlsValue == "tls" ||
            tlsValue == "none"
        ) {
            "Mode TLS non pris en charge : $tlsValue"
        }

        val tlsEnabled = tlsValue == "tls"

        val insecure = if (json.has("insecure")) {
            json.value("insecure")
        } else {
            json.value("allowInsecure")
        }

        val fingerprint = json.value("fp").trim()

        if (tlsEnabled && fingerprint.isNotBlank()) {
            require(fingerprint in allowedFingerprints) {
                "Empreinte TLS non prise en charge : $fingerprint"
            }
        }

        return VmessConfig(
            name = json.value("ps").ifBlank {
                Uri.decode(originalLink.substringAfter("#", ""))
            },
            server = server,
            port = port,
            uuid = uuid,
            alterId = alterId,
            security = security,
            transport = transport,
            rawHttp = rawHttp,
            path = finalPath,
            host = finalHost,
            tlsEnabled = tlsEnabled,
            serverName = json.value("sni").trim(),
            alpn = splitList(json.value("alpn"), ","),
            allowInsecure = parseBoolean(insecure, "insecure"),
            fingerprint = if (tlsEnabled) fingerprint else "",
            cipherSuites = if (tlsEnabled) {
                splitList(json.value("cs"), ":")
            } else emptyList(),
            globalPadding = extras.globalPadding,
            authenticatedLength = extras.authenticatedLength,
            packetEncoding = extras.packetEncoding,
            serviceName = extras.serviceName.ifBlank {
                if (transport == "grpc") path else ""
            },
            transportMethod = extras.method,
            transportHeaders = extras.headers,
            idleTimeout = extras.idleTimeout,
            pingTimeout = extras.pingTimeout,
            maxEarlyData = extras.maxEarlyData,
            earlyDataHeaderName = extras.earlyDataHeaderName,
            multiplex = extras.multiplex
        )
    }

    private fun parseUrlConfig(link: String): VmessConfig {
        val uri = Uri.parse(link)
        val server = uri.host.orEmpty()
        val port = if (uri.port > 0) uri.port else 443
        val uuid = uri.userInfo.orEmpty().substringBefore(":")

        validateServer(server, port, uuid)

        val security = normalizeSecurity(
            uri.getQueryParameter("encryption").orEmpty()
        )

        val tlsMode = uri.getQueryParameter("security")
            .orEmpty().lowercase()

        require(tlsMode != "reality") {
            "VMess Reality n'est pas encore pris en charge par cet importateur."
        }

        val tlsEnabled = tlsMode == "tls" ||
            tlsMode == "true" || tlsMode == "1"

        val transport = normalizeTransport(
            uri.getQueryParameter("type").orEmpty(),
            false
        )

        val path = uri.getQueryParameter("path").orEmpty()
        val host = uri.getQueryParameter("host").orEmpty()

        val sni = uri.getQueryParameter("sni")
            ?: uri.getQueryParameter("peer").orEmpty()

        val fingerprint = uri.getQueryParameter("fp").orEmpty()

        if (tlsEnabled && fingerprint.isNotBlank()) {
            require(fingerprint in allowedFingerprints) {
                "Empreinte TLS non prise en charge : $fingerprint"
            }
        }

        val headers = parseHeaders(
            uri.getQueryParameter("headers").orEmpty()
        )

        val mux = parseMultiplex(uri)

        return VmessConfig(
            name = Uri.decode(link.substringAfter("#", "")),
            server = server,
            port = port,
            uuid = uuid,
            alterId = uri.getQueryParameter("alterId")
                ?.toIntOrNull() ?: 0,
            security = security,
            transport = transport,
            rawHttp = false,
            path = path,
            host = host,
            tlsEnabled = tlsEnabled,
            serverName = sni,
            alpn = splitList(
                uri.getQueryParameter("alpn").orEmpty(), ","
            ),
            allowInsecure = parseBoolean(
                uri.getQueryParameter("insecure")
                    ?: uri.getQueryParameter("allow_insecure")
                    ?: uri.getQueryParameter("allowInsecure").orEmpty(),
                "insecure"
            ),
            fingerprint = if (tlsEnabled) fingerprint else "",
            cipherSuites = if (tlsEnabled) {
                splitList(
                    uri.getQueryParameter("cs")
                        ?: uri.getQueryParameter("tls_cipher_suites").orEmpty(),
                    ",:"
                )
            } else emptyList(),
            globalPadding = parseBoolean(
                uri.getQueryParameter("globalPadding").orEmpty(),
                "globalPadding"
            ),
            authenticatedLength = parseBoolean(
                uri.getQueryParameter("authenticatedLength").orEmpty(),
                "authenticatedLength"
            ),
            packetEncoding = normalizePacketEncoding(
                uri.getQueryParameter("packetEncoding").orEmpty()
            ),
            serviceName = uri.getQueryParameter("serviceName")
                ?: if (transport == "grpc") path else "",
            transportMethod = uri.getQueryParameter("method").orEmpty(),
            transportHeaders = headers,
            idleTimeout = uri.getQueryParameter("idle_timeout").orEmpty(),
            pingTimeout = uri.getQueryParameter("ping_timeout").orEmpty(),
            maxEarlyData = uri.getQueryParameter("max_early_data")
                ?.toIntOrNull() ?: 0,
            earlyDataHeaderName = uri.getQueryParameter(
                "early_data_header_name"
            ).orEmpty(),
            multiplex = mux,
            disableSni = parseBoolean(
                uri.getQueryParameter("disable_sni").orEmpty(),
                "disable_sni"
            )
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
            outbound.put("packet_encoding", config.packetEncoding)
        }

        if (config.tlsEnabled) {
            val tls = JSONObject().put("enabled", true)

            if (config.serverName.isNotBlank() && !config.disableSni) {
                tls.put("server_name", config.serverName)
            }

            if (config.allowInsecure) {
                tls.put("insecure", true)
            }

            if (config.alpn.isNotEmpty()) {
                tls.put("alpn", JSONArray(config.alpn))
            }

            if (config.cipherSuites.isNotEmpty()) {
                tls.put("cipher_suites", JSONArray(config.cipherSuites))
            }

            if (config.fingerprint.isNotBlank()) {
                tls.put(
                    "utls",
                    JSONObject().put("enabled", true)
                        .put("fingerprint", config.fingerprint)
                )
            }

            outbound.put("tls", tls)
        }

        buildTransport(config)?.let {
            outbound.put("transport", it)
        }

        buildMultiplex(config.multiplex)?.let {
            outbound.put("multiplex", it)
        }

        return outbound
    }

    private fun buildTransport(c: VmessConfig): JSONObject? {
        if (c.transport == "tcp") return null

        val result = JSONObject().put("type", c.transport)

        when (c.transport) {
            "ws", "httpupgrade" -> {
                if (c.path.isNotBlank()) {
                    result.put("path", c.path)
                }

                val headers = JSONObject()

                if (c.host.isNotBlank()) {
                    headers.put("Host", c.host)
                }

                c.transportHeaders.forEach { (k, v) ->
                    headers.put(k, v)
                }

                if (headers.length() > 0) {
                    result.put("headers", headers)
                }

                if (c.transport == "ws" && c.maxEarlyData > 0) {
                    result.put("max_early_data", c.maxEarlyData)
                }

                if (c.earlyDataHeaderName.isNotBlank()) {
                    result.put(
                        "early_data_header_name",
                        c.earlyDataHeaderName
                    )
                }
            }

            "http" -> {
                if (c.host.isNotBlank()) {
                    result.put(
                        "host",
                        JSONArray(c.host.split(",").map { it.trim() })
                    )
                }

                if (c.path.isNotBlank()) {
                    result.put("path", c.path)
                }

                if (c.transportMethod.isNotBlank()) {
                    result.put("method", c.transportMethod)
                } else if (c.rawHttp) {
                    result.put("method", "GET")
                }

                val headers = JSONObject()

                c.transportHeaders.forEach { (k, v) ->
                    headers.put(k, v)
                }

                if (headers.length() > 0) {
                    result.put("headers", headers)
                }

                if (c.idleTimeout.isNotBlank()) {
                    result.put("idle_timeout", c.idleTimeout)
                }

                if (c.pingTimeout.isNotBlank()) {
                    result.put("ping_timeout", c.pingTimeout)
                }
            }

            "grpc" -> {
                val service = c.serviceName.ifBlank { c.path }

                if (service.isNotBlank()) {
                    result.put("service_name", service)
                }

                if (c.idleTimeout.isNotBlank()) {
                    result.put("idle_timeout", c.idleTimeout)
                }

                if (c.pingTimeout.isNotBlank()) {
                    result.put("ping_timeout", c.pingTimeout)
                }
            }

            "quic" -> {
                require(c.tlsEnabled) {
                    "Le transport QUIC exige TLS."
                }
            }

            else -> throw IllegalArgumentException(
                "Transport non pris en charge : ${c.transport}"
            )
        }

        return result
    }

    private fun buildMultiplex(m: MultiplexConfig): JSONObject? {
        if (!m.specified) return null

        if (!m.enabled) {
            return JSONObject().put("enabled", false)
        }

        val result = JSONObject().put("enabled", true)

        if (m.protocol.isNotBlank()) {
            result.put("protocol", m.protocol)
        }

        if (m.maxConnections > 0) {
            result.put("max_connections", m.maxConnections)
        }

        if (m.minStreams > 0) {
            result.put("min_streams", m.minStreams)
        }

        if (m.maxStreams > 0) {
            result.put("max_streams", m.maxStreams)
        }

        if (m.padding) {
            result.put("padding", true)
        }

        if (m.brutalEnabled) {
            require(m.brutalUpMbps > 0 && m.brutalDownMbps > 0) {
                "Les débits Brutal doivent être supérieurs à zéro."
            }

            result.put(
                "brutal",
                JSONObject().put("enabled", true)
                    .put("up_mbps", m.brutalUpMbps)
                    .put("down_mbps", m.brutalDownMbps)
            )
        }

        return result
    }

    private data class ExtraOptions(
        val globalPadding: Boolean = false,
        val authenticatedLength: Boolean = false,
        val packetEncoding: String = "",
        val transport: String = "",
        val path: String = "",
        val host: String = "",
        val method: String = "",
        val serviceName: String = "",
        val headers: Map<String, String> = emptyMap(),
        val idleTimeout: String = "",
        val pingTimeout: String = "",
        val maxEarlyData: Int = 0,
        val earlyDataHeaderName: String = "",
        val multiplex: MultiplexConfig = MultiplexConfig()
    )

    private fun parseExtra(raw: String): ExtraOptions {
        if (raw.isBlank()) return ExtraOptions()

        val uri = Uri.parse("https://localhost/?$raw")

        val known = setOf(
            "globalPadding", "authenticatedLength", "packetEncoding",
            "type", "headerType", "host", "path", "method", "headers",
            "idle_timeout", "ping_timeout", "max_early_data",
            "early_data_header_name", "serviceName", "mux",
            "mux_protocol", "mux_max_connections", "mux_min_streams",
            "mux_max_streams", "mux_padding", "brutal_enabled",
            "brutal_up_mbps", "brutal_down_mbps"
        )

        val unknown = uri.queryParameterNames - known

        require(unknown.isEmpty()) {
            "Options avancées inconnues : ${unknown.joinToString(", ")}"
        }

        val transport = uri.getQueryParameter("type").orEmpty()
        val mux = parseMultiplex(uri)

        return ExtraOptions(
            globalPadding = parseBoolean(
                uri.getQueryParameter("globalPadding").orEmpty(),
                "globalPadding"
            ),
            authenticatedLength = parseBoolean(
                uri.getQueryParameter("authenticatedLength").orEmpty(),
                "authenticatedLength"
            ),
            packetEncoding = normalizePacketEncoding(
                uri.getQueryParameter("packetEncoding").orEmpty()
            ),
            transport = if (transport.isBlank()) "" else
                normalizeTransport(transport, false),
            path = uri.getQueryParameter("path").orEmpty(),
            host = uri.getQueryParameter("host").orEmpty(),
            method = uri.getQueryParameter("method").orEmpty(),
            serviceName = uri.getQueryParameter("serviceName").orEmpty(),
            headers = parseHeaders(
                uri.getQueryParameter("headers").orEmpty()
            ),
            idleTimeout = uri.getQueryParameter("idle_timeout").orEmpty(),
            pingTimeout = uri.getQueryParameter("ping_timeout").orEmpty(),
            maxEarlyData = uri.getQueryParameter("max_early_data")
                ?.toIntOrNull() ?: 0,
            earlyDataHeaderName = uri.getQueryParameter(
                "early_data_header_name"
            ).orEmpty(),
            multiplex = mux
        )
    }

    private fun parseMultiplex(uri: Uri): MultiplexConfig {
        val keys = setOf(
            "mux", "mux_protocol", "mux_max_connections",
            "mux_min_streams", "mux_max_streams", "mux_padding",
            "brutal_enabled", "brutal_up_mbps", "brutal_down_mbps"
        )

        val specified = uri.queryParameterNames.any { it in keys }

        if (!specified) return MultiplexConfig()

        val brutal = parseBoolean(
            uri.getQueryParameter("brutal_enabled").orEmpty(),
            "brutal_enabled"
        )

        return MultiplexConfig(
            specified = true,
            enabled = parseBoolean(
                uri.getQueryParameter("mux").orEmpty(), "mux"
            ),
            protocol = uri.getQueryParameter("mux_protocol").orEmpty(),
            maxConnections = uri.getQueryParameter(
                "mux_max_connections"
            )?.toIntOrNull() ?: 0,
            minStreams = uri.getQueryParameter(
                "mux_min_streams"
            )?.toIntOrNull() ?: 0,
            maxStreams = uri.getQueryParameter(
                "mux_max_streams"
            )?.toIntOrNull() ?: 0,
            padding = parseBoolean(
                uri.getQueryParameter("mux_padding").orEmpty(),
                "mux_padding"
            ),
            brutalEnabled = brutal,
            brutalUpMbps = uri.getQueryParameter(
                "brutal_up_mbps"
            )?.toIntOrNull() ?: if (brutal) 1 else 0,
            brutalDownMbps = uri.getQueryParameter(
                "brutal_down_mbps"
            )?.toIntOrNull() ?: if (brutal) 1 else 0
        )
    }

    private fun parseHeaders(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()

        val parts = raw.split("|")

        require(parts.size % 2 == 0) {
            "La liste des en-têtes HTTP est incomplète."
        }

        val result = linkedMapOf<String, String>()

        parts.chunked(2).forEach {
            val key = it[0].trim()

            require(key.isNotBlank()) {
                "Nom d'en-tête vide."
            }

            result[key] = it[1]
        }

        return result
    }

    private fun normalizeTransport(
        value: String,
        rawHttp: Boolean
    ): String {
        if (rawHttp) return "http"

        return when (value.lowercase().trim()) {
            "", "tcp", "raw" -> "tcp"
            "ws", "websocket" -> "ws"
            "h2", "http" -> "http"
            "grpc" -> "grpc"
            "quic" -> "quic"
            "httpupgrade" -> "httpupgrade"
            else -> throw IllegalArgumentException(
                "Transport non pris en charge : $value"
            )
        }
    }

    private fun normalizeSecurity(value: String): String {
        val security = value.trim().lowercase().ifBlank {
            "auto"
        }

        require(security in allowedSecurity) {
            "Sécurité VMess non prise en charge : $security"
        }

        return security
    }

    private fun normalizePacketEncoding(value: String): String {
        val encoding = value.trim().lowercase()

        require(encoding in setOf("", "none", "packetaddr", "xudp")) {
            "Encodage de paquets inconnu : $encoding"
        }

        return if (encoding == "none") "" else encoding
    }

    private fun validateServer(
        server: String,
        port: Int,
        uuid: String
    ) {
        require(
            server.isNotBlank() &&
            !server.any { it.isWhitespace() } &&
            !server.contains("://")
        ) {
            "Adresse du serveur invalide."
        }

        require(port in 1..65535) {
            "Port hors limites."
        }

        require(uuidPattern.matches(uuid)) {
            "UUID VMess invalide."
        }
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

    private fun splitList(
        value: String,
        separators: String
    ): List<String> {
        if (value.isBlank()) return emptyList()

        return value.split(Regex("[$separators]"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun decodeBase64(payload: String): String {
        val normalized = payload.replace("\\s".toRegex(), "")
            .replace('-', '+')
            .replace('_', '/')

        val padded = normalized +
            "=".repeat((4 - normalized.length % 4) % 4)

        return Base64.decode(padded, Base64.DEFAULT)
            .toString(Charsets.UTF_8)
    }

    private fun JSONObject.value(key: String): String {
        if (!has(key) || isNull(key)) return ""

        return opt(key)?.toString().orEmpty()
    }
}
