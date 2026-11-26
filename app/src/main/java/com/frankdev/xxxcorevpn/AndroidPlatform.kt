
package com.frankdev.xxxcorevpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.system.OsConstants
import io.throneproj.mobile.ConnectionOwner
import io.throneproj.mobile.InterfaceUpdateListener
import io.throneproj.mobile.LocalDNSTransport
import io.throneproj.mobile.NetworkInterface as CoreNetworkInterface
import io.throneproj.mobile.NetworkInterfaceIterator
import io.throneproj.mobile.Notification
import io.throneproj.mobile.PlatformInterface
import io.throneproj.mobile.StringIterator
import io.throneproj.mobile.TunOptions
import io.throneproj.mobile.WIFIState
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.NetworkInterface as JavaNetworkInterface

class AndroidPlatform(
    private val vpnService: FrankVpnService
) : PlatformInterface {

    private val connectivity =
        vpnService.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun localDNSTransport(): LocalDNSTransport? = null

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        if (!vpnService.protect(fd)) {
            error("Unable to protect network socket")
        }
    }

    override fun openTun(options: TunOptions): Int {
        return vpnService.openTun(options)
    }

    override fun useProcFS(): Boolean = true

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): ConnectionOwner {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            error("Connection owner lookup requires Android 10 or newer")
        }

        val uid = connectivity.getConnectionOwnerUid(
            ipProtocol,
            InetSocketAddress(sourceAddress, sourcePort),
            InetSocketAddress(destinationAddress, destinationPort)
        )

        if (uid == Process.INVALID_UID) {
            error("Connection owner not found")
        }

        val packages =
            vpnService.packageManager.getPackagesForUid(uid)?.toList()
                ?: emptyList()

        return ConnectionOwner().apply {
            userId = uid
            userName = packages.firstOrNull() ?: ""
            setAndroidPackageNames(ArrayStringIterator(packages))
        }
    }

    override fun packageNamesByUid(uid: Int): StringIterator {
        val packages =
            vpnService.packageManager.getPackagesForUid(uid)?.toList()
                ?: emptyList()

        return ArrayStringIterator(packages)
    }

    override fun startDefaultInterfaceMonitor(
        listener: InterfaceUpdateListener
    ) {
        closeDefaultInterfaceMonitor(listener)

        val callback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                updateDefaultInterface(listener, network)
            }

            override fun onLost(network: Network) {
                val activeNetwork = connectivity.activeNetwork
                if (activeNetwork != null) {
                    updateDefaultInterface(listener, activeNetwork)
                } else {
                    listener.updateDefaultInterface("", -1, false, false)
                }
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: android.net.LinkProperties
            ) {
                updateDefaultInterface(listener, network)
            }
        }

        networkCallback = callback

        try {
            connectivity.registerDefaultNetworkCallback(callback)

            val activeNetwork = connectivity.activeNetwork
            if (activeNetwork != null) {
                updateDefaultInterface(listener, activeNetwork)
            } else {
                listener.updateDefaultInterface("", -1, false, false)
            }
        } catch (e: Exception) {
            networkCallback = null
            listener.updateDefaultInterface("", -1, false, false)
            throw e
        }
    }

    override fun closeDefaultInterfaceMonitor(
        listener: InterfaceUpdateListener
    ) {
        val callback = networkCallback ?: return

        try {
            connectivity.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }

        networkCallback = null
    }

    private fun updateDefaultInterface(
        listener: InterfaceUpdateListener,
        network: Network
    ) {
        try {
            val properties = connectivity.getLinkProperties(network)
                ?: return

            val name = properties.interfaceName ?: return
            val javaInterface = JavaNetworkInterface.getByName(name)
                ?: return

            val capabilities =
                connectivity.getNetworkCapabilities(network)

            val metered = capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_METERED
            ) == false

            listener.updateDefaultInterface(
                name,
                javaInterface.index,
                metered,
                false
            )
        } catch (_: Exception) {
            listener.updateDefaultInterface("", -1, false, false)
        }
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val result = mutableListOf<CoreNetworkInterface>()

        val javaInterfaces = mutableMapOf<String, JavaNetworkInterface>()

        try {
            val enumeration = JavaNetworkInterface.getNetworkInterfaces()

            while (enumeration != null && enumeration.hasMoreElements()) {
                val item = enumeration.nextElement()
                javaInterfaces[item.name] = item
            }
        } catch (_: Exception) {
        }

        try {
            for (network in connectivity.allNetworks) {
                try {
                    val properties =
                        connectivity.getLinkProperties(network) ?: continue

                    val capabilities =
                        connectivity.getNetworkCapabilities(network) ?: continue

                    val name = properties.interfaceName ?: continue
                    val javaInterface = javaInterfaces[name] ?: continue

                    val item = CoreNetworkInterface()

                    item.name = name
                    item.index = javaInterface.index
                    item.mtu = try {
                        javaInterface.mtu
                    } catch (_: Exception) {
                        1500
                    }

                    item.addresses = ArrayStringIterator(
                        javaInterface.interfaceAddresses.mapNotNull {
                            val address = it.address
                                ?: return@mapNotNull null

                            // Correction du plantage IPv6 :
                            // Android peut retourner une adresse avec une zone
                            // comme fe80::1234%tun0.
                            // ThroneCore refuse cette zone dans un préfixe CIDR.
                            val host = if (address is Inet6Address) {
                                address.hostAddress.substringBefore('%')
                            } else {
                                address.hostAddress
                            }

                            "$host/${it.networkPrefixLength}"
                        }
                    )

                    item.dnsServer = ArrayStringIterator(
                        properties.dnsServers.mapNotNull {
                            it.hostAddress
                        }
                    )

                    item.gateway = ArrayStringIterator(
                        properties.routes
                            .filter { it.destination.prefixLength == 0 }
                            .mapNotNull { it.gateway }
                            .filterNot { it.isAnyLocalAddress }
                            .mapNotNull { it.hostAddress }
                    )

                    item.type = when {
                        capabilities.hasTransport(
                            NetworkCapabilities.TRANSPORT_WIFI
                        ) -> 0

                        capabilities.hasTransport(
                            NetworkCapabilities.TRANSPORT_CELLULAR
                        ) -> 1

                        capabilities.hasTransport(
                            NetworkCapabilities.TRANSPORT_ETHERNET
                        ) -> 2

                        else -> 3
                    }

                    var flags = 0

                    if (capabilities.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_INTERNET
                        )
                    ) {
                        flags = flags or OsConstants.IFF_UP
                        flags = flags or OsConstants.IFF_RUNNING
                    }

                    if (javaInterface.isLoopback) {
                        flags = flags or OsConstants.IFF_LOOPBACK
                    }

                    if (javaInterface.isPointToPoint) {
                        flags = flags or OsConstants.IFF_POINTOPOINT
                    }

                    if (javaInterface.supportsMulticast()) {
                        flags = flags or OsConstants.IFF_MULTICAST
                    }

                    item.flags = flags

                    item.metered = !capabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_NOT_METERED
                    )

                    result.add(item)

                } catch (_: Exception) {
                    continue
                }
            }
        } catch (_: Exception) {
        }

        return CoreNetworkInterfaceArray(result)
    }

    override fun readWIFIState(): WIFIState? = null

    override fun clearDNSCache() {
    }

    override fun sendNotification(notification: Notification) {
    }

    override fun cancelNotification(
        identifier: String,
        typeID: Int
    ) {
    }
}

private class ArrayStringIterator(
    private val values: List<String>
) : StringIterator {

    private var position = 0

    override fun hasNext(): Boolean {
        return position < values.size
    }

    override fun next(): String {
        if (!hasNext()) {
            return ""
        }

        return values[position++]
    }

    override fun len(): Int {
        return values.size
    }
}

private class CoreNetworkInterfaceArray(
    private val values: List<CoreNetworkInterface>
) : NetworkInterfaceIterator {

    private var position = 0

    override fun hasNext(): Boolean {
        return position < values.size
    }

    override fun next(): CoreNetworkInterface {
        if (!hasNext()) {
            error("No more network interfaces")
        }

        return values[position++]
    }
}
