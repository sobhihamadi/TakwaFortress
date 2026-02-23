package com.example.takwafortress.services.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.example.takwafortress.repository.implementations.LocalBlockedAppRepository
import com.example.takwafortress.services.core.DeviceOwnerService
import com.example.takwafortress.util.constants.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ✅ ENHANCED: Monitor app installations and automatically block pre-blocked apps
 */
class AppInstallMonitorService(private val context: Context) {

    companion object {
        private const val TAG = "${AppConstants.LOG_TAG}.AppInstallMonitor"
    }

    private val repository = LocalBlockedAppRepository(context)
    private val deviceOwnerService = DeviceOwnerService(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Renamed internal state to avoid conflict with the public method
    private var monitoringActive = false

    /**
     * Public method to check monitoring status.
     * Called by SettingsViewModel.
     */
    fun isMonitoring(): Boolean {
        return monitoringActive
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            val packageName = intent.data?.schemeSpecificPart ?: return

            when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED -> {
                    if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        onAppInstalled(packageName)
                    }
                }
                Intent.ACTION_PACKAGE_REPLACED -> {
                    onAppUpdated(packageName)
                }
                Intent.ACTION_PACKAGE_REMOVED -> {
                    onAppUninstalled(packageName)
                }
            }
        }
    }

    /**
     * Start monitoring app installations
     */
    fun startMonitoring() {
        if (monitoringActive) return

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }

            context.registerReceiver(packageReceiver, filter)
            monitoringActive = true

            Log.i(TAG, "✅ App install monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start monitoring", e)
        }
    }

    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        if (!monitoringActive) return

        try {
            context.unregisterReceiver(packageReceiver)
            monitoringActive = false
            Log.i(TAG, "App install monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop monitoring", e)
        }
    }

    private fun onAppInstalled(packageName: String) {
        scope.launch {
            try {
                Log.d(TAG, "App installed: $packageName")

                val isBlocked = repository.isAppBlocked(packageName)

                if (isBlocked) {
                    Log.w(TAG, "⚠️ Blocked app detected: $packageName")
                    val blockedApp = repository.getByPackageName(packageName)

                    if (blockedApp.getIsPreBlocked() || !blockedApp.getIsInstalled()) {
                        Log.i(TAG, "🚫 Auto-blocking pre-blocked app: ${blockedApp.getAppName()}")

                        if (deviceOwnerService.isDeviceOwner()) {
                            blockAppNow(packageName, blockedApp.isBlacklistedApp())

                            val updatedApp = com.example.takwafortress.model.builders.IdentifierBlockedAppBuilder.newBuilder()
                                .setId(blockedApp.getId())
                                .setBlockedApp(
                                    com.example.takwafortress.model.builders.BlockedAppBuilder.newBuilder()
                                        .setPackageName(blockedApp.getPackageName())
                                        .setAppName(blockedApp.getAppName())
                                        .setIsSystemApp(blockedApp.getIsSystemApp())
                                        .setIsSuspended(true)
                                        .setBlockReason("Auto-blocked on install")
                                        .setDetectedDate(System.currentTimeMillis())
                                        .build()
                                )
                                .build()

                            repository.update(updatedApp)
                            Log.i(TAG, "✅ Successfully auto-blocked ${blockedApp.getAppName()}")
                            showBlockNotification(blockedApp.getAppName())
                        } else {
                            Log.e(TAG, "❌ Device Owner required to block apps")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling app installation: $packageName", e)
            }
        }
    }

    private fun blockAppNow(packageName: String, isNuclear: Boolean) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val adminComponent = android.content.ComponentName(
                context,
                com.example.takwafortress.receivers.DeviceAdminReceiver::class.java
            )

            if (isNuclear) {
                dpm.setApplicationHidden(adminComponent, packageName, true)
                Log.d(TAG, "Hidden nuclear app: $packageName")
            } else {
                dpm.setPackagesSuspended(adminComponent, arrayOf(packageName), true)
                Log.d(TAG, "Suspended app: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to block app: $packageName", e)
        }
    }

    private fun showBlockNotification(appName: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

            val notification = android.app.Notification.Builder(context, AppConstants.CHANNEL_ID_VIOLATIONS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🚫 App Blocked")
                .setContentText("$appName was automatically blocked per your settings")
                .setPriority(android.app.Notification.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notification", e)
        }
    }

    private fun onAppUpdated(packageName: String) {
        scope.launch {
            try {
                val isBlocked = repository.isAppBlocked(packageName)
                if (isBlocked) {
                    val blockedApp = repository.getByPackageName(packageName)
                    blockAppNow(packageName, blockedApp.isBlacklistedApp())
                    Log.i(TAG, "Re-blocked updated app: $packageName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling app update: $packageName", e)
            }
        }
    }

    private fun onAppUninstalled(packageName: String) {
        scope.launch {
            try {
                val isBlocked = repository.isAppBlocked(packageName)
                if (isBlocked) {
                    Log.i(TAG, "Blocked app uninstalled: $packageName (keeping in block list)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling app uninstall: $packageName", e)
            }
        }
    }

    fun applyAllBlocks() {
        scope.launch {
            try {
                if (!deviceOwnerService.isDeviceOwner()) {
                    Log.w(TAG, "Device Owner required to apply blocks")
                    return@launch
                }

                val blockedApps = repository.getAll()
                Log.i(TAG, "Applying ${blockedApps.size} blocks...")

                blockedApps.forEach { app ->
                    if (app.getIsInstalled()) {
                        blockAppNow(app.getPackageName(), app.isBlacklistedApp())
                    }
                }
                Log.i(TAG, "✅ All blocks applied")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply all blocks", e)
            }
        }
    }
}