
package com.frankdev.xxxcorevpn

import io.throneproj.mobile.ConnectionOwner
import io.throneproj.mobile.InterfaceUpdateListener
import io.throneproj.mobile.LocalDNSTransport
import io.throneproj.mobile.NetworkInterface
import io.throneproj.mobile.NetworkInterfaceIterator
import io.throneproj.mobile.Notification
import io.throneproj.mobile.PlatformInterface
import io.throneproj.mobile.StringIterator
import io.throneproj.mobile.TunOptions
import io.throneproj.mobile.WIFIState

class AndroidPlatform(
    private val vpnService: FrankVpnService
) : PlatformInterface {

    override fun localDNSTransport(): LocalDNSTransport? = null

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        if (!vpnService.protect(fd)) {
            error("Unable to protect socket")
        }
    }

    override fun openTun(options: TunOptions): Int {
        error("TUN implementation pending")
    }

    override fun useProcFS(): Boolean = true

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): ConnectionOwner {
        error("Connection owner lookup not implemented")
    }

    override fun packageNamesByUid(uid: Int): StringIterator {
        return EmptyStringIterator()
    }

    override fun startDefaultInterfaceMonitor(
        listener: InterfaceUpdateListener
    ) {
        listener.updateDefaultInterface("", -1, false, false)
    }

    override fun closeDefaultInterfaceMonitor(
        listener: InterfaceUpdateListener
    ) {
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        return EmptyNetworkInterfaceIterator()
    }

    override fun readWIFIState(): WIFIState? = null

    override fun clearDNSCache() {
    }

    override fun sendNotification(notification: Notification) {
    }

    override fun cancelNotification(identifier: String, typeID: Int) {
    }
}

private class EmptyStringIterator : StringIterator {
    override fun hasNext(): Boolean = false
    override fun next(): String = ""
    override fun len(): Int = 0
}

private class EmptyNetworkInterfaceIterator : NetworkInterfaceIterator {
    override fun hasNext(): Boolean = false
    override fun next(): NetworkInterface {
        error("No network interface available")
    }
}
