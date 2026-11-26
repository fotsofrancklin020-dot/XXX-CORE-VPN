
package com.frankdev.xxxcorevpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.IpPrefix
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.io.File
import java.net.InetAddress
import org.json.JSONArray
import org.json.JSONObject

import io.throneproj.mobile.Mobile
import io.throneproj.mobile.SetupOptions
import io.throneproj.mobile.StartOptions
import io.throneproj.mobile.Instance
import io.throneproj.mobile.RoutePrefix
import io.throneproj.mobile.RoutePrefixIterator
import io.throneproj.mobile.StringIterator
import io.throneproj.mobile.TunOptions

class FrankVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.frankdev.xxxcorevpn.CONNECT"
        const val ACTION_DISCONNECT = "com.frankdev.xxxcorevpn.DISCONNECT"
        const val EXTRA_OUTBOUND_JSON = "outbound_json"

        private const val CHANNEL_ID = "xxxcore_vpn_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var throneWorkingDir: File? = null
    private var throneTempDir: File? = null
    private var connection: ParcelFileDescriptor? = null
    private var instance: Instance? = null
    private var starting = false

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {
            ACTION_DISCONNECT -> {
                stopTunnel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_CONNECT -> {
                if (instance != null || starting) {
                    return START_NOT_STICKY
                }

                val outboundJson =
                    intent.getStringExtra(EXTRA_OUTBOUND_JSON)

                if (outboundJson.isNullOrBlank()) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }

                try {
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification()
                    )

                    startTunnel(outboundJson)

                } catch (e: Exception) {
                    stopTunnel()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startTunnel(outboundJson: String) {
        if (starting || instance != null) return

        starting = true

        try {
            if (VpnService.prepare(this) != null) {
                error("Autorisation VPN non accordée")
            }

            throneWorkingDir = File(filesDir, "throne")
            throneTempDir = File(cacheDir, "throne")

            throneWorkingDir!!.mkdirs()
            throneTempDir!!.mkdirs()

            val setup = SetupOptions().apply {
                basePath = filesDir.absolutePath
                workingPath = throneWorkingDir!!.absolutePath
                tempPath = throneTempDir!!.absolutePath
                logMaxLines = 300
                debug = false
            }

            Mobile.setup(setup)

            val coreConfig = buildCoreConfig(outboundJson)

            val startOptions = StartOptions().apply {
                coreConfig = this@FrankVpnService
                    .let { coreConfig }
            }

            val engine = Mobile.newInstance(
                AndroidPlatform(this),
                startOptions
            )

            try {
                engine.start()
                instance = engine
            } catch (e: Exception) {
                engine.close()
                throw e
            }

        } finally {
            starting = false
        }
    }

    private fun buildCoreConfig(outboundJson: String): String {

        val proxy = JSONObject(outboundJson)

        require(proxy.has("type")) {
            "Type de protocole absent"
        }

        require(proxy.has("server") && proxy.has("server_port")) {
            "Adresse ou port du serveur absent"
        }

        proxy.put("tag", "proxy")

        val tun = JSONObject().apply {
            put("type", "tun")
            put("tag", "tun-in")
            put("interface_name", "xxxcore")
            put("address", JSONArray().apply {
                put("172.19.0.1/30")
                put("fdfe:dcba:9876::1/126")
            })
            put("mtu", 1500)
            put("auto_route", true)
            put("strict_route", false)
            put("dns_mode", "hijack")
            put("dns_address", JSONArray().apply {
                put("1.1.1.1")
                put("8.8.8.8")
            })
        }

        val direct = JSONObject().apply {
            put("type", "direct")
            put("tag", "direct")
        }

        val dns = JSONObject().apply {
            put("servers", JSONArray().put(
                JSONObject().apply {
                    put("type", "udp")
                    put("tag", "dns")
                    put("server", "1.1.1.1")
                }
            ))
            put("final", "dns")
        }

        val route = JSONObject().apply {
            put("final", "proxy")
        }

        return JSONObject().apply {
            put("log", JSONObject().put("level", "warn"))
            put("dns", dns)
            put("inbounds", JSONArray().put(tun))
            put("outbounds", JSONArray().apply {
                put(proxy)
                put(direct)
            })
            put("route", route)
        }.toString()
    }

    private fun stopTunnel() {
        try {
            instance?.close()
        } catch (_: Exception) {
        }

        instance = null
        closeTun()
    }

    private fun createNotification(): Notification {
        val manager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "XXX-CORE VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("XXX-CORE VPN")
            .setContentText("Tunnel VPN en fonctionnement")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .build()
    }

    fun openTun(options: TunOptions): Int {

        if (prepare(this) != null) {
            error("VPN permission not granted")
        }

        val builder = Builder()
            .setSession("XXX-CORE VPN")
            .setMtu(options.getMTU())

        options.getInet4Address().forEach {
            builder.addAddress(it.address(), it.prefix())
        }

        options.getInet6Address().forEach {
            builder.addAddress(it.address(), it.prefix())
        }

        if (options.getAutoRoute()) {

            if (options.getDNSMode().value != Mobile.DNSModeDisabled) {
                options.getDNSServerAddress().toList().forEach {
                    builder.addDnsServer(it)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                val ipv4 = options.getInet4RouteAddress()

                if (ipv4.hasNext()) {
                    ipv4.forEach {
                        builder.addRoute(it.address(), it.prefix())
                    }
                } else if (options.getInet4Address().hasNext()) {
                    builder.addRoute("0.0.0.0", 0)
                }

                val ipv6 = options.getInet6RouteAddress()

                if (ipv6.hasNext()) {
                    ipv6.forEach {
                        builder.addRoute(it.address(), it.prefix())
                    }
                } else if (options.getInet6Address().hasNext()) {
                    builder.addRoute("::", 0)
                }

                options.getInet4RouteExcludeAddress().forEach {
                    val address = InetAddress.getByName(it.address())
                    if (!address.isLoopbackAddress) {
                        builder.excludeRoute(
                            IpPrefix(address, it.prefix())
                        )
                    }
                }

                options.getInet6RouteExcludeAddress().forEach {
                    val address = InetAddress.getByName(it.address())
                    if (!address.isLoopbackAddress) {
                        builder.excludeRoute(
                            IpPrefix(address, it.prefix())
                        )
                    }
                }

            } else {

                options.getInet4RouteRange().forEach {
                    builder.addRoute(it.address(), it.prefix())
                }

                options.getInet6RouteRange().forEach {
                    builder.addRoute(it.address(), it.prefix())
                }
            }
        }

        val includePackages = options.getIncludePackage()

        if (includePackages.hasNext()) {
            val added = includePackages.toList().count {
                try {
                    builder.addAllowedApplication(it)
                    true
                } catch (_: PackageManager.NameNotFoundException) {
                    false
                }
            }

            if (added == 0) {
                builder.addAllowedApplication(packageName)
            }
        }

        options.getExcludePackage().toList().forEach {
            try {
                builder.addDisallowedApplication(it)
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            options.isHTTPProxyEnabled()
        ) {
            builder.setHttpProxy(
                ProxyInfo.buildDirectProxy(
                    options.getHTTPProxyServer(),
                    options.getHTTPProxyServerPort(),
                    options.getHTTPProxyBypassDomain().toList()
                        .filter { it.isNotBlank() }
                )
            )
        }

        val tun = builder.establish()
            ?: error("Unable to establish VPN interface")

        connection?.close()
        connection = tun

        return tun.fd
    }

    fun closeTun() {
        try {
            connection?.close()
        } catch (_: Exception) {
        }
        connection = null
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }
}

private inline fun RoutePrefixIterator.forEach(
    block: (RoutePrefix) -> Unit
) {
    while (hasNext()) {
        block(next())
    }
}

private fun StringIterator.toList(): List<String> {
    val values = ArrayList<String>()

    while (hasNext()) {
        values.add(next())
    }

    return values
}
