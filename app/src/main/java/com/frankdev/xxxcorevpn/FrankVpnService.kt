package com.frankdev.xxxcorevpn

import android.os.Handler
import android.os.Looper
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
import android.util.Log
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
        private const val TAG = "XXXCORE-VPN"
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
    intent: Intent?, flags: Int, startId: Int
): Int {
    when (intent?.action) {

        ACTION_DISCONNECT -> {
            Log.i(TAG, "Déconnexion demandée")
            stopTunnel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        ACTION_CONNECT -> {
            Log.i(TAG, "Demande de connexion reçue")

            if (instance != null || starting) {
                Log.w(TAG, "Connexion déjà active ou démarrage en cours")
                return START_NOT_STICKY
            }

            val outboundJson =
                intent.getStringExtra(EXTRA_OUTBOUND_JSON)

            if (outboundJson.isNullOrBlank()) {
                Log.e(TAG, "Configuration outbound absente")
                stopSelf(startId)
                return START_NOT_STICKY
            }

            starting = true
            showForegroundNotification("Préparation du moteur VPN...")

            Thread({
                try {
                    startTunnel(outboundJson)

                } catch (e: Throwable) {
                    Log.e(TAG, "ÉCHEC DU DÉMARRAGE VPN", e)

                    stopTunnel()

                    runOnServiceMainThread {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        showErrorNotification(
                            e.message ?: e.javaClass.simpleName
                        )
                        stopSelf(startId)
                    }

                } finally {
                    starting = false
                }
            }, "XXXCORE-VPN-START").start()
        }
    }

    return START_NOT_STICKY
}

    private fun startTunnel(outboundJson: String) {

         {
            Log.i(TAG, "Étape 1 : vérification de l'autorisation VPN")

            if (VpnService.prepare(this) != null) {
                error("Autorisation VPN non accordée")
            }

            Log.i(TAG, "Étape 1 terminée")
            Log.i(TAG, "Étape 2 : préparation des répertoires")

            throneWorkingDir = File(filesDir, "throne")
            throneTempDir = File(cacheDir, "throne")

            throneWorkingDir!!.mkdirs()
            throneTempDir!!.mkdirs()

            Log.i(TAG, "Étape 2 terminée")
            Log.i(TAG, "Étape 3 : initialisation de ThroneCore")

            val setup = SetupOptions().apply {
                basePath = filesDir.absolutePath
                workingPath = throneWorkingDir!!.absolutePath
                tempPath = throneTempDir!!.absolutePath
                logMaxLines = 300
                debug = true
            }

            Mobile.setup(setup)

            Log.i(TAG, "Étape 3 terminée")
            Log.i(TAG, "Étape 4 : construction de la configuration")

            val coreConfig = buildCoreConfig(outboundJson)

            Log.i(TAG, "Étape 4 terminée")
            Log.i(TAG, "Étape 5 : création des options du moteur")

            val startOptions = StartOptions()
            startOptions.setCoreConfig(coreConfig)

            Log.i(TAG, "Étape 5 terminée")
            Log.i(TAG, "Étape 6 : création de l'instance ThroneCore")

            val engine = Mobile.newInstance(
                AndroidPlatform(this),
                startOptions
            )

            Log.i(TAG, "Étape 6 terminée")

            try {
                Log.i(TAG, "Étape 7 : lancement du moteur VPN")

                showForegroundNotification("Démarrage du moteur VPN...")

                engine.start()

                Log.i(TAG, "Étape 7 terminée : moteur démarré")

                instance = engine

                showForegroundNotification("Moteur VPN démarré")

            } catch (e: Throwable) {
                Log.e(TAG, "Erreur pendant engine.start()", e)

                try {
                    engine.close()
                } catch (closeError: Throwable) {
                    Log.e(TAG, "Erreur fermeture moteur", closeError)
                }

                throw e
            }

    }

    private fun buildCoreConfig(outboundJson: String): String {
        Log.i(TAG, "Analyse de la configuration proxy")

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
            put("log", JSONObject().put("level", "debug"))
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
        Log.i(TAG, "Arrêt du tunnel VPN")

        try {
            instance?.close()
        } catch (e: Throwable) {
            Log.e(TAG, "Erreur pendant la fermeture du moteur", e)
        }

        instance = null
        closeTun()

        Log.i(TAG, "Tunnel VPN arrêté")
    }

    private fun createNotification(
        message: String,
        ongoing: Boolean = true
    ): Notification {
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
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(ongoing)
            .build()
    }

    private fun showForegroundNotification(message: String) {
        val notification = createNotification(message)

        startForeground(NOTIFICATION_ID, notification)

        Log.i(TAG, "Notification : $message")
    }

    private fun showErrorNotification(message: String) {
        val manager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        manager.notify(
            NOTIFICATION_ID,
            createNotification("Échec VPN : $message", false)
        )
    }

    fun openTun(options: TunOptions): Int {
        Log.i(TAG, "Création de l'interface TUN demandée")

        if (prepare(this) != null) {
            error("VPN permission not granted")
        }

        showForegroundNotification("Création de l'interface VPN...")

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
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Package exclu introuvable : $it")
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

        Log.i(TAG, "Établissement de l'interface TUN")

        val tun = builder.establish()
            ?: error("Unable to establish VPN interface")

        connection?.close()
        connection = tun

        Log.i(TAG, "Interface TUN établie avec succès")

        return tun.fd
    }

    fun closeTun() {
        Log.i(TAG, "Fermeture de l'interface TUN")

        try {
            connection?.close()
        } catch (e: Throwable) {
            Log.e(TAG, "Erreur fermeture TUN", e)
        }

        connection = null
    }

private fun runOnServiceMainThread(action: () -> Unit) {
    Handler(Looper.getMainLooper()).post(action)
}

override fun onDestroy() {
    Log.i(TAG, "Service VPN détruit")

    stopTunnel()

    super.onDestroy()
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