
package com.frankdev.xxxcorevpn

import android.net.VpnService
import android.content.Intent
import android.os.IBinder
import android.os.Build
import android.net.IpPrefix
import android.net.ProxyInfo
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import io.throneproj.mobile.Mobile
import io.throneproj.mobile.RoutePrefix
import io.throneproj.mobile.RoutePrefixIterator
import io.throneproj.mobile.StringIterator
import io.throneproj.mobile.TunOptions
import java.net.InetAddress

class FrankVpnService : VpnService() {

    private var connection: ParcelFileDescriptor? = null

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return START_NOT_STICKY
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
                builder.excludeRoute(IpPrefix(address, it.prefix()))
            }
        }

        options.getInet6RouteExcludeAddress().forEach {
            val address = InetAddress.getByName(it.address())
            if (!address.isLoopbackAddress) {
                builder.excludeRoute(IpPrefix(address, it.prefix()))
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
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
        connection?.close()
        connection = null
    }

    override fun onDestroy() {
        closeTun()
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
