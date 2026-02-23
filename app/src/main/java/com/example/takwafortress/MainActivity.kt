package com.example.takwafortress

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.takwafortress.services.core.DeviceOwnerService
import com.example.takwafortress.services.monitoring.AppInstallMonitorService
import com.example.takwafortress.util.constants.AppConstants
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Taqwa Application Class - The entry point of the app.
 */
class TaqwaApplication : Application() {

    companion object {
        private const val TAG = AppConstants.LOG_TAG
        lateinit var instance: TaqwaApplication
            private set
    }

    private lateinit var deviceOwnerService: DeviceOwnerService
    private lateinit var appInstallMonitorService: AppInstallMonitorService

    override fun onCreate() {
        super.onCreate()

        // ✅ CRITICAL: Register BouncyCastle FIRST
        registerBouncyCastle()

        instance = this

        Log.i(TAG, "🚀 Taqwa Fortress starting...")
        Log.i(TAG, "Version: ${AppConstants.APP_VERSION}")

        initializeServices()
        createNotificationChannels()

        Log.i(TAG, "✅ Taqwa Fortress initialized successfully")
    }

    /**
     * ✅ IMPROVED: Robust BouncyCastle registration
     */
    private fun registerBouncyCastle() {
        try {
            val existingProvider = Security.getProvider("BC")

            if (existingProvider == null) {
                // Insert at position 1 (highest priority)
                Security.insertProviderAt(BouncyCastleProvider(), 1)
                Log.i(TAG, "✅ BouncyCastle registered at position 1")
            } else {
                Log.i(TAG, "✅ BouncyCastle already registered: ${existingProvider.name} v${existingProvider.version}")
            }

            // Verify it actually worked
            val bcProvider = Security.getProvider("BC")
            if (bcProvider == null) {
                throw RuntimeException("BouncyCastle registration failed!")
            }

            // Log available signature algorithms for debugging
            Log.d(TAG, "Available BC Signature algorithms:")
            bcProvider.services
                .filter { it.type == "Signature" }
                .take(5)
                .forEach { Log.d(TAG, "  - ${it.algorithm}") }

        } catch (e: Exception) {
            Log.e(TAG, "❌ FATAL: BouncyCastle registration failed", e)
            throw RuntimeException("BouncyCastle registration failed: ${e.message}", e)
        }
    }

    private fun initializeServices() {
        Log.i(TAG, "Initializing services...")

        deviceOwnerService = DeviceOwnerService(this)
        appInstallMonitorService = AppInstallMonitorService(this)

        appInstallMonitorService.startMonitoring()

        Log.i(TAG, "Services initialized")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val fortressChannel = NotificationChannel(
                AppConstants.CHANNEL_ID_FORTRESS,
                "Fortress Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows fortress protection status and countdown"
                setShowBadge(false)
            }

            val violationsChannel = NotificationChannel(
                AppConstants.CHANNEL_ID_VIOLATIONS,
                "Security Violations",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for blocked apps and bypass attempts"
                setShowBadge(true)
            }

            val systemChannel = NotificationChannel(
                AppConstants.CHANNEL_ID_SYSTEM,
                "System Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "License status, updates, and system messages"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(
                listOf(fortressChannel, violationsChannel, systemChannel)
            )

            Log.i(TAG, "Notification channels created")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appInstallMonitorService.stopMonitoring()
        Log.i(TAG, "Taqwa Fortress terminated")
    }

    fun getDeviceOwnerService(): DeviceOwnerService = deviceOwnerService
    fun getAppInstallMonitorService(): AppInstallMonitorService = appInstallMonitorService
}