package com.example.takwafortress.services.security

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.IBinder
import android.util.Log

/**
 * Foreground service that listens for ADB Wireless Debugging mDNS broadcasts.
 *
 * Two service types are watched:
 *  • _adb-tls-pairing._tcp  → appears only when the "Pair with code" popup is open
 *  • _adb-tls-connect._tcp  → always present while Wireless Debugging is ON
 *
 * When the pairing service is detected, a high-priority notification with an
 * inline RemoteInput is posted so the user can type the 6-digit code without
 * ever leaving the Settings screen (which would close the popup).
 */
class AdbDiscoveryService : Service() {

    companion object {
        const val TAG              = "AdbDiscoveryService"
        const val CHANNEL_ID       = "adb_pairing_channel"
        const val NOTIF_FOREGROUND = 10
        const val NOTIF_PAIRING    = 11
        const val KEY_CODE_INPUT   = "KEY_PAIRING_CODE"
        const val ACTION_CODE      = "com.example.takwafortress.ACTION_RECEIVE_CODE"

        private const val SVC_PAIRING = "_adb-tls-pairing._tcp"
        private const val SVC_CONNECT = "_adb-tls-connect._tcp"
    }

    private lateinit var nsdManager: NsdManager

    // Discovered values — set from the mDNS resolve callbacks
    var pairingPort: Int    = -1
    var connectPort: Int    = -1
    var deviceIp:   String  = "127.0.0.1"

    // ── mDNS listeners ───────────────────────────────────────────────────────

    private val pairingDiscoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Pairing discovery start failed: $errorCode")
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onDiscoveryStarted(serviceType: String) {
            Log.d(TAG, "Listening for pairing service...")
        }
        override fun onDiscoveryStopped(serviceType: String) {}

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "Pairing service found: ${serviceInfo.serviceName}")
            resolveService(serviceInfo, isPairing = true)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "Pairing service lost — popup was closed")
            pairingPort = -1
            // Cancel the pending notification; the code is now invalid
            getSystemService(NotificationManager::class.java)?.cancel(NOTIF_PAIRING)
        }
    }

    private val connectDiscoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onDiscoveryStarted(serviceType: String) {}
        override fun onDiscoveryStopped(serviceType: String) {}

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            resolveService(serviceInfo, isPairing = false)
        }

        override fun onServiceLost(servicdebueInfo: NsdServiceInfo) {
            connectPort = -1
        }
    }

    // ── Resolve helper ───────────────────────────────────────────────────────

    private fun resolveService(serviceInfo: NsdServiceInfo, isPairing: Boolean) {
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed (isPairing=$isPairing): $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val port = serviceInfo.port
                val host = serviceInfo.host?.hostAddress ?: "127.0.0.1"
                Log.d(TAG, "Resolved (isPairing=$isPairing): host=$host port=$port")

                if (isPairing) {
                    pairingPort = port
                    deviceIp    = host
                    showPairingNotification()          // ← prompt user for code
                } else {
                    connectPort = port
                    deviceIp    = host
                }
            }
        })
    }
    private var isDiscovering = false  // ← ADD THIS

    // ── Service lifecycle ────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        nsdManager = getSystemService(NSD_SERVICE) as NsdManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_FOREGROUND, buildForegroundNotification())

        if (!isDiscovering) {
            isDiscovering = true
        nsdManager.discoverServices(SVC_PAIRING, NsdManager.PROTOCOL_DNS_SD, pairingDiscoveryListener)
        nsdManager.discoverServices(SVC_CONNECT, NsdManager.PROTOCOL_DNS_SD, connectDiscoveryListener)
        } else {
            Log.d(TAG, "AdbDiscoveryService started, listening for mDNS broadcasts")
        }
            return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isDiscovering = false  // ← RESET on destroy

        runCatching { nsdManager.stopServiceDiscovery(pairingDiscoveryListener) }
        runCatching { nsdManager.stopServiceDiscovery(connectDiscoveryListener) }
        Log.d(TAG, "AdbDiscoveryService stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notifications ────────────────────────────────────────────────────────

    /**
     * Posts a notification with an inline RemoteInput so the user can type the
     * pairing code directly from the notification shade without dismissing the
     * Settings dialog.
     */
    private fun showPairingNotification() {
        val remoteInput = RemoteInput.Builder(KEY_CODE_INPUT)
            .setLabel("Enter 6-digit pairing code")
            .build()

        val replyIntent = Intent(ACTION_CODE).apply {
            setClass(this@AdbDiscoveryService, NotificationReplyReceiver::class.java)
            putExtra("PAIRING_PORT", pairingPort)
            putExtra("CONNECT_PORT", connectPort)
            putExtra("DEVICE_IP",    deviceIp)
        }

        val replyPi = PendingIntent.getBroadcast(
            this, 0, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val action = Notification.Action.Builder(
            null, "Pair Now", replyPi
        ).addRemoteInput(remoteInput).build()

        val notif = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📱 ADB Pairing Ready!")
            .setContentText("Port $pairingPort detected. Enter the 6-digit code to activate.")
            .addAction(action)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        getSystemService(NotificationManager::class.java)?.notify(NOTIF_PAIRING, notif)
        Log.d(TAG, "Pairing notification posted for port $pairingPort")
    }

    private fun buildForegroundNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Waiting for Wireless Debugging…")
            .setContentText("Open Settings → Wireless Debugging → Pair with code")
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ADB Pairing",
            NotificationManager.IMPORTANCE_HIGH
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}