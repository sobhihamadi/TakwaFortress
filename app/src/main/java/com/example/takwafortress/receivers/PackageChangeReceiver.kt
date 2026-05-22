package com.example.takwafortress.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.takwafortress.services.filtering.BlockedAppsManager
import com.example.takwafortress.services.filtering.ContentFilteringService
import com.example.takwafortress.util.constants.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "${AppConstants.LOG_TAG}_PackageReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action      = intent.action ?: return
        val packageName = intent.data?.schemeSpecificPart ?: return

        Log.d(TAG, "📦 Package event received: $action → $packageName")

        // 🛡️ FIX 1: Keep the broadcast window alive during async processing
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    Intent.ACTION_PACKAGE_ADDED    -> handlePackageAdded(context, packageName)
                    Intent.ACTION_PACKAGE_REPLACED -> handlePackageReplaced(context, packageName)
                    Intent.ACTION_PACKAGE_REMOVED  -> handlePackageRemoved(context, packageName)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing package event: ${e.message}", e)
            } finally {
                // Crucial: Tell the OS it's safe to reclaim or sleep the process
                pendingResult.finish()
            }
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private fun handlePackageAdded(context: Context, packageName: String) {
        Log.d(TAG, "App installed: $packageName")

        if (packageName == ContentFilteringService.CHROME_PACKAGE) {
            Log.d(TAG, "✅ Chrome installed — allowed, skipping")
            return
        }

        val isBrowser   = isBrowserApp(context, packageName)
        val isPreBlocked = BlockedAppsManager(context).isPackageBlocked(packageName)

        Log.d(TAG, "  isBrowser=$isBrowser  isPreBlocked=$isPreBlocked")

        if (isBrowser || isPreBlocked) {
            Log.w(TAG, "🚫 Blocking newly installed app: $packageName")
            autoBlockApp(context, packageName)
        }
    }

    private fun handlePackageReplaced(context: Context, packageName: String) {
        if (packageName == ContentFilteringService.CHROME_PACKAGE) return

        val isBrowser    = isBrowserApp(context, packageName)
        val isPreBlocked = BlockedAppsManager(context).isPackageBlocked(packageName)

        if (isBrowser || isPreBlocked) {
            Log.w(TAG, "⚠️ Blocked app updated — re-applying block: $packageName")
            autoBlockApp(context, packageName)
        }
    }

    private fun handlePackageRemoved(context: Context, packageName: String) {
        if (BlockedAppsManager(context).isPackageBlocked(packageName)) {
            Log.i(TAG, "✅ Blocked app uninstalled: $packageName")
        }
    }

    private fun isBrowserApp(context: Context, packageName: String): Boolean {
        val pm = context.packageManager

        val httpIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com")).apply {
            setPackage(packageName)
        }
        val httpInfo = pm.resolveActivity(httpIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (httpInfo != null) {
            Log.d(TAG, "  → $packageName handles http://")
            return true
        }

        val httpsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
            setPackage(packageName)
        }
        val httpsInfo = pm.resolveActivity(httpsIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (httpsInfo != null) {
            Log.d(TAG, "  → $packageName handles https://")
            return true
        }

        return false
    }

    // ── Block execution ───────────────────────────────────────────────────────

    // 🛡️ FIX 2: Removed CoroutineScope wrap. This now runs safely inline
    // within the parent IO context created in onReceive.
    private fun autoBlockApp(context: Context, packageName: String) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                    as android.app.admin.DevicePolicyManager
            val adminComponent = android.content.ComponentName(
                context,
                com.example.takwafortress.receivers.DeviceAdminReceiver::class.java
            )

            if (!dpm.isDeviceOwnerApp(context.packageName)) {
                Log.e(TAG, "❌ NOT Device Owner — cannot block $packageName")
                return
            }

            val hidden = dpm.setApplicationHidden(adminComponent, packageName, true)
            Log.i(TAG, if (hidden) "✅ Hidden: $packageName" else "⚠️ Hide failed, trying suspend: $packageName")

            if (!hidden && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val failedPackages = dpm.setPackagesSuspended(
                    adminComponent,
                    arrayOf(packageName),
                    true
                )
                if (failedPackages.isEmpty()) {
                    Log.i(TAG, "✅ Suspended: $packageName")
                } else {
                    Log.e(TAG, "❌ Both hide and suspend failed: $packageName")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception blocking $packageName: ${e.message}", e)
        }
    }
}