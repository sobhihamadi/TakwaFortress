package com.example.takwafortress.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.takwafortress.services.filtering.AppSuspensionService
import com.example.takwafortress.services.filtering.BlockedAppsManager
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

        Log.i(TAG, "Package event: $action for $packageName")

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

        if (blockedAppsManager.isPackageBlocked(packageName)) {
            Log.w(TAG, "⚠️ Blocked app installed: $packageName - Auto-blocking")
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
            val appSuspensionService = AppSuspensionService(context)

            // Simply suspend/hide the app
            appSuspensionService.suspendApps(listOf(packageName))

            Log.i(TAG, "App blocked: $packageName")
        }
    }
}