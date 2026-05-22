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
        val blockedAppsManager = BlockedAppsManager(context)
        val browserDetectionService = BrowserDetectionService(context)

        val isPreBlocked = blockedAppsManager.isPackageBlocked(packageName)
        val isBrowser = browserDetectionService.isBrowserApp(packageName)

        // ✅ FIX: Skip Chrome — it's the allowed browser
        val isChrome = packageName == "com.android.chrome"

        if (!isChrome && (isPreBlocked || isBrowser)) {
            // ✅ NO delay — block immediately
            autoBlockApp(context, packageName)
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

                if (!dpm.isDeviceOwnerApp(context.packageName)) {
                    Log.e(TAG, "❌ NOT Device Owner — cannot block $packageName")
                    return@launch
                }

                // ✅ FIX: Use setApplicationHidden for ALL browsers (not suspend)
                // Suspend only grays the icon — hidden completely removes it
                val hidden = dpm.setApplicationHidden(adminComponent, packageName, true)
                Log.i(TAG, if (hidden) "✅ Hidden: $packageName" else "⚠️ Hide failed, trying suspend: $packageName")

                if (!hidden) {
                    // Fallback to suspend if hide fails (e.g. system browser)
                    val failed = dpm.setPackagesSuspended(
                        adminComponent,
                        arrayOf(packageName),
                        true
                    )
                    Log.i(TAG, if (failed.isEmpty()) "✅ Suspended: $packageName"
                    else "❌ Both hide and suspend failed: $packageName")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception blocking $packageName: ${e.message}", e)
            }
        }
    }
}