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

        // ✅ FIX: Check both the custom blocklist AND dynamic browser detection
        val isInBlockList = blockedAppsManager.isPackageBlocked(packageName)
        val isBrowser = isNewBrowserPackage(context, packageName)

        if (isInBlockList || isBrowser) {
            Log.w(TAG, "⚠️ Blocked/browser app installed: $packageName — Auto-blocking")
            autoBlockApp(context, packageName)

            // Persist to BlockedAppsManager so it's caught next time too
            if (isBrowser) {
                blockedAppsManager.addBlockedPackage(packageName)
            }
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
    private fun isNewBrowserPackage(context: Context, packageName: String): Boolean {
        if (packageName == "com.android.chrome" || packageName == context.packageName) return false
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://www.google.com")
                addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                setPackage(packageName)
            }
            val matches = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    intent,
                    android.content.pm.PackageManager.ResolveInfoFlags.of(
                        android.content.pm.PackageManager.MATCH_ALL.toLong()
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(
                    intent, android.content.pm.PackageManager.MATCH_ALL
                )
            }
            matches.isNotEmpty()
        } catch (e: Exception) { false }
    }
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