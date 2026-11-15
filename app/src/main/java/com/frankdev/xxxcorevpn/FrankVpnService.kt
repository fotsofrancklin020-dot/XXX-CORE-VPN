package com.frankdev.xxxcorevpn

import android.net.VpnService
import android.content.Intent
import android.os.IBinder

class FrankVpnService : VpnService() {

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        // Le moteur VPN sera ajouté après l'intégration
        // et les tests des protocoles.

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
