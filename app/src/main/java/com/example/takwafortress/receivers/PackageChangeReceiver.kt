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

/**
 * Package Change Receiver — Detects app installations and uninstallations.
 *
 * Key fix: browser detection now uses intent resolution (asking the system
 * "can this app handle http:// URLs?") instead of a static package-name list.
 * This catches ANY browser — including obscure or brand-new ones.
 */
class PackageChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "${AppConstants.LOG_TAG}_PackageReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action      = intent.action ?: return
        val packageName = intent.data?.schemeSpecificPart ?: return

        Log.d(TAG, "📦 Package event: $action → $packageName")

        when (action) {
            Intent.ACTION_PACKAGE_ADDED    -> handlePackageAdded(context, packageName)
            Intent.ACTION_PACKAGE_REPLACED -> handlePackageReplaced(context, packageName)
            Intent.ACTION_PACKAGE_REMOVED  -> handlePackageRemoved(context, packageName)
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private fun handlePackageAdded(context: Context, packageName: String) {
        Log.d(TAG, "App installed: $packageName")

        // Chrome is the only allowed browser — never touch it
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

    // ── Dynamic browser detection ─────────────────────────────────────────────

    /**
     * Returns true if [packageName] can handle http:// or https:// URLs.
     * This is the reliable way to detect ANY browser without a hardcoded list.
     */
    private fun isBrowserApp(context: Context, packageName: String): Boolean {
        val pm = context.packageManager

        // Test 1 — can it open http:// URLs?
        val httpIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com")).apply {
            setPackage(packageName)
        }
        val httpInfo = pm.resolveActivity(httpIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (httpInfo != null) {
            Log.d(TAG, "  → $packageName handles http://")
            return true
        }

        // Test 2 — can it open https:// URLs?
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

                // Primary: hide the app completely (invisible in launcher)
                val hidden = dpm.setApplicationHidden(adminComponent, packageName, true)
                Log.i(TAG, if (hidden) "✅ Hidden: $packageName" else "⚠️ Hide failed, trying suspend: $packageName")

                if (!hidden) {
                    // Fallback: suspend (grey icon, still visible but unlaunchable)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception blocking $packageName: ${e.message}", e)
            }
        }
    }
}