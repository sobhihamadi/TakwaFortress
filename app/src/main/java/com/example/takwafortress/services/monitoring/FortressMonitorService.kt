package com.example.takwafortress.services.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.takwafortress.services.core.DeviceOwnerService
import com.example.takwafortress.services.filtering.ContentFilteringService
import com.example.takwafortress.util.constants.AppConstants

/**
 * FortressMonitorService — long-running foreground service.
 *
 * Responsibilities:
 *  1. Periodically re-scan for any browsers that were installed or un-hidden
 *     after fortress activation and re-block them immediately.
 *  2. Acts as a persistent watchdog so protections survive across reboots
 *     and Google Play auto-updates.
 *
 * Re-scan interval: every 3 minutes. This is short enough to catch a freshly
 * installed browser before the user can browse, but light enough not to drain
 * battery.
 */
class FortressMonitorService : Service() {

    companion object {
        private const val TAG                = "${AppConstants.LOG_TAG}_Monitor"
        private const val NOTIFICATION_ID    = 1001
        private const val RESCAN_INTERVAL_MS = 3 * 60 * 1000L   // 3 minutes
        private const val CHANNEL_ID         = AppConstants.CHANNEL_ID_FORTRESS
    }

    private lateinit var contentFilteringService: ContentFilteringService
    private lateinit var deviceOwnerService: DeviceOwnerService
    private val handler = Handler(Looper.getMainLooper())

    private val rescanRunnable = object : Runnable {
        override fun run() {
            performRescan()
            handler.postDelayed(this, RESCAN_INTERVAL_MS)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        contentFilteringService = ContentFilteringService(this)
        deviceOwnerService      = DeviceOwnerService(this)
        Log.i(TAG, "FortressMonitorService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        // Run the first rescan immediately, then repeat every RESCAN_INTERVAL_MS
        handler.removeCallbacks(rescanRunnable)
        handler.post(rescanRunnable)

        Log.i(TAG, "✅ FortressMonitorService started — rescanning every ${RESCAN_INTERVAL_MS / 60_000}min")
        return START_STICKY   // restart if killed by the system
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(rescanRunnable)
        Log.i(TAG, "FortressMonitorService stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Core rescan logic ─────────────────────────────────────────────────────

    /**
     * Called on every rescan tick. Runs on the main thread (safe for DPM calls).
     * Does nothing if Device Owner is not active.
     */
    private fun performRescan() {
        if (!deviceOwnerService.isDeviceOwner()) {
            Log.d(TAG, "Rescan skipped — not Device Owner")
            return
        }

        Log.d(TAG, "🔄 Periodic browser rescan…")
        try {
            contentFilteringService.reBlockAnyEscapedBrowsers()
        } catch (e: Exception) {
            Log.e(TAG, "Rescan error: ${e.message}")
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildForegroundNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Fortress Status",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Shows fortress protection status"
                        setShowBadge(false)
                    }
                )
            }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("🛡️ Taqwa Fortress Active")
                .setContentText("Monitoring device protection…")
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("🛡️ Taqwa Fortress Active")
                .setContentText("Monitoring device protection…")
                .setOngoing(true)
                .build()
        }
    }
}