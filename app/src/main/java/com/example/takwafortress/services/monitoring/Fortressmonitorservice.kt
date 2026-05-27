package com.example.takwafortress.services.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.takwafortress.receivers.DeviceAdminReceiver
import com.example.takwafortress.services.core.DeviceOwnerService
import com.example.takwafortress.services.filtering.ContentFilteringService

/**
 * FortressMonitorService
 *
 * A persistent foreground service that runs continuously while the fortress
 * is active. Its ONLY job is Layer 3: block any new browser the moment it
 * is installed — without requiring the user to tap "Activate Full Protection".
 *
 * Why a Service instead of a manifest BroadcastReceiver?
 * -------------------------------------------------------
 * Manifest receivers on Android 8+ are background-process-restricted and
 * can be killed before they finish. A foreground service keeps the process
 * alive and registers a runtime BroadcastReceiver that fires reliably even
 * when the main app UI is not open.
 *
 * Lifecycle:
 *  - Started by FortressActivationService after Device Owner is confirmed.
 *  - Restarted on boot via BootCompletedReceiver (15-second delay).
 *  - Stopped by FortressClearService when the commitment period ends.
 */
class FortressMonitorService : Service() {

    companion object {
        private const val TAG                 = "FortressMonitorService"
        private const val NOTIFICATION_ID     = 9901
        private const val CHANNEL_ID          = "fortress_monitor_channel"
        const val  CHROME_PACKAGE             = "com.android.chrome"

        /** Start the service (idempotent — safe to call multiple times). */
        fun start(context: Context) {
            val intent = Intent(context, FortressMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Stop the service (called during fortress clear). */
        fun stop(context: Context) {
            context.stopService(Intent(context, FortressMonitorService::class.java))
        }
    }

    // ── Android services ──────────────────────────────────────────────────────

    private lateinit var devicePolicyManager : DevicePolicyManager
    private lateinit var adminComponent      : ComponentName
    private lateinit var deviceOwnerService  : DeviceOwnerService
    private lateinit var contentFiltering    : ContentFilteringService

    // ── Runtime receiver for package events ───────────────────────────────────

    /**
     * This receiver runs inside the foreground service process, so it is
     * NOT subject to the Android 8+ background execution limits that affect
     * statically registered manifest receivers.
     */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED    -> onPackageInstalled(pkg)
                Intent.ACTION_PACKAGE_REPLACED -> onPackageInstalled(pkg)  // app updated
            }
        }
    }

    private var receiverRegistered = false

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent      = ComponentName(this, DeviceAdminReceiver::class.java)
        deviceOwnerService  = DeviceOwnerService(this)
        contentFiltering    = ContentFilteringService(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerPackageReceiver()

        Log.i(TAG, "✅ FortressMonitorService started — Layer 3 browser blocking is LIVE")

        // Block any browsers that may have been installed while the service
        // was not running (e.g. device rebooted, service was killed).
        blockAllCurrentBrowsers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: if the system kills this service (memory pressure),
        // Android will automatically restart it with a null intent.
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterPackageReceiver()
        Log.i(TAG, "FortressMonitorService stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Package install handler ───────────────────────────────────────────────

    /**
     * Called the moment a new package is installed or updated.
     * If it is a browser (can handle http/https intents) and is NOT Chrome,
     * it is hidden immediately via Device Owner API.
     */
    private fun onPackageInstalled(packageName: String) {
        // Skip our own app and Chrome
        if (packageName == applicationContext.packageName) return
        if (packageName == CHROME_PACKAGE) return

        if (!deviceOwnerService.isDeviceOwner()) {
            Log.w(TAG, "Device Owner not active — cannot block $packageName")
            return
        }

        if (isBrowser(packageName)) {
            val blocked = hidePackage(packageName)
            if (blocked) {
                Log.i(TAG, "🛡️ Auto-blocked new browser: $packageName")
            } else {
                Log.w(TAG, "⚠️ Failed to hide browser: $packageName")
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Scans currently installed packages and hides any browser that is not Chrome.
     * Runs synchronously on the calling thread (called from onCreate on the main
     * thread — fast enough since PackageManager queries are local).
     */
    private fun blockAllCurrentBrowsers() {
        if (!deviceOwnerService.isDeviceOwner()) return

        val result = contentFiltering.blockOtherBrowsersDynamic()
        Log.i(TAG, "Startup browser scan: ${result.blocked} blocked, ${result.notInstalled} preserved")
    }

    /**
     * Returns true if [packageName] can handle http/https web links — i.e. it
     * is a browser. We intentionally query the PackageManager directly (not a
     * hardcoded list) so this works for ANY browser, including ones we have
     * never seen before.
     */
    private fun isBrowser(packageName: String): Boolean {
        return try {
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.google.com")
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage(packageName)
            }
            val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    webIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(webIntent, PackageManager.MATCH_ALL)
            }
            matches.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "isBrowser check failed for $packageName: ${e.message}")
            false
        }
    }

    /**
     * Hides [packageName] via Device Owner setApplicationHidden.
     * Returns true on success.
     */
    private fun hidePackage(packageName: String): Boolean {
        return try {
            devicePolicyManager.setApplicationHidden(adminComponent, packageName, true)
        } catch (e: Exception) {
            Log.e(TAG, "hidePackage failed for $packageName: ${e.message}")
            false
        }
    }

    // ── BroadcastReceiver registration ────────────────────────────────────────

    private fun registerPackageReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
        }
        receiverRegistered = true
        Log.d(TAG, "Package receiver registered")
    }

    private fun unregisterPackageReceiver() {
        if (!receiverRegistered) return
        try {
            unregisterReceiver(packageReceiver)
            receiverRegistered = false
            Log.d(TAG, "Package receiver unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "unregisterReceiver: ${e.message}")
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fortress Monitor",
                NotificationManager.IMPORTANCE_MIN       // silent, no heads-up
            ).apply {
                description     = "Keeps browser blocking active in the background"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Taqwa Fortress Active")
            .setContentText("Browser blocking is running in the background")
            .setOngoing(true)
            .build()
    }
}