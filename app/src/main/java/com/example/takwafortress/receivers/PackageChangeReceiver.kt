package com.example.takwafortress.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.takwafortress.services.filtering.AppSuspensionService
import com.example.takwafortress.services.filtering.BlockedAppsManager
import com.example.takwafortress.services.monitoring.BrowserDetectionService
import com.example.takwafortress.util.constants.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Package Change Receiver - Detects app installations/uninstallations.
 * Automatically blocks newly installed apps if they're in the custom blocked list.
 */
class PackageChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "${AppConstants.LOG_TAG}_PackageReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val packageName = intent.data?.schemeSpecificPart ?: return

        Log.e(TAG, "🔥 PACKAGE EVENT RECEIVED: $action → $packageName")  // ADD THIS

        when (action) {
            Intent.ACTION_PACKAGE_ADDED -> handlePackageAdded(context, packageName)
            Intent.ACTION_PACKAGE_REPLACED -> handlePackageReplaced(context, packageName)
            Intent.ACTION_PACKAGE_REMOVED -> handlePackageRemoved(context, packageName)
        }
    }

    /**
     * Handles new package installation.
     */
    private fun handlePackageAdded(context: Context, packageName: String) {
        Log.e(TAG, "📦 handlePackageAdded: $packageName")

        val blockedAppsManager = BlockedAppsManager(context)
        val browserDetectionService = BrowserDetectionService(context)

        val isPreBlocked = blockedAppsManager.isPackageBlocked(packageName)
        val isBrowser = browserDetectionService.isBrowserApp(packageName)

        Log.e(TAG, "  isPreBlocked=$isPreBlocked  isBrowser=$isBrowser")

        if (isPreBlocked || isBrowser) {
            // Delay slightly — package needs to fully register before suspension works
            Handler(Looper.getMainLooper()).postDelayed({
                autoBlockApp(context, packageName)
            }, 2000L) // 2 second delay
        }
    }
    /**
     * Handles package replacement (app update).
     */
    private fun handlePackageReplaced(context: Context, packageName: String) {
        val blockedAppsManager = BlockedAppsManager(context)

        if (blockedAppsManager.isPackageBlocked(packageName)) {
            Log.w(TAG, "⚠️ Blocked app updated: $packageName - Re-applying blocks")
            autoBlockApp(context, packageName)
        }
    }

    /**
     * Handles package removal.
     */
    private fun handlePackageRemoved(context: Context, packageName: String) {
        val blockedAppsManager = BlockedAppsManager(context)

        if (blockedAppsManager.isPackageBlocked(packageName)) {
            Log.i(TAG, "✅ Blocked app uninstalled: $packageName")
        }
    }

    /**
     * Automatically blocks a newly installed app.
     */
    private fun autoBlockApp(context: Context, packageName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                        as android.app.admin.DevicePolicyManager
                val adminComponent = android.content.ComponentName(
                    context,
                    com.example.takwafortress.receivers.DeviceAdminReceiver::class.java
                )

                // Check Device Owner first
                val isOwner = dpm.isDeviceOwnerApp(context.packageName)
                Log.e(TAG, "  isDeviceOwner=$isOwner for blocking $packageName")

                if (!isOwner) {
                    Log.e(TAG, "❌ NOT Device Owner — cannot block $packageName")
                    return@launch
                }

                // Suspend the app
                val failed = dpm.setPackagesSuspended(
                    adminComponent,
                    arrayOf(packageName),
                    true
                )

                if (failed.isEmpty()) {
                    Log.e(TAG, "✅ Successfully suspended: $packageName")
                } else {
                    Log.e(TAG, "❌ Failed to suspend: $packageName — trying hide instead")
                    // Fallback: hide it completely
                    dpm.setApplicationHidden(adminComponent, packageName, true)
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception blocking $packageName: ${e.message}", e)
            }
        }
    }
}