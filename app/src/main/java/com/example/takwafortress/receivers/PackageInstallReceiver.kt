package com.example.takwafortress.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.takwafortress.services.core.DeviceOwnerService
import com.example.takwafortress.services.filtering.ContentFilteringService
import com.example.takwafortress.util.constants.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PackageInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageInstallReceiver"
        private const val NOTIF_ID_BASE = 9000

        // ── PM indexing delay ─────────────────────────────────────────────────
        // Android doesn't finish registering a new app's intent filters
        // in PackageManager before PACKAGE_ADDED fires.
        // 3 seconds is enough on all tested devices.
        private const val PM_INDEX_DELAY_MS = 3_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action   = intent.action
        val dataUri  = intent.data

        if ((action != Intent.ACTION_PACKAGE_ADDED &&
                    action != Intent.ACTION_PACKAGE_REPLACED) || dataUri == null) return

        val packageName = dataUri.schemeSpecificPart ?: return

        // Never touch Chrome or our own app
        if (packageName == ContentFilteringService.CHROME_PACKAGE ||
            packageName == context.packageName) return

        Log.d(TAG, "📦 Package event: $action → $packageName")

        // goAsync() keeps the receiver process alive while we do async work
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                autoBlock(context, packageName)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Unhandled error for $packageName", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Three-phase auto-block strategy:
     *
     * Phase 1 — Immediate: hide if the package is in our hardcoded list.
     *            Works at t=0 because we don't touch PackageManager.
     *
     * Phase 2 — Delayed intent query: wait for PM to finish indexing
     *            the new app, then check if it handles web URLs.
     *            Catches unknown/new browsers not in our list.
     *
     * Phase 3 — Full re-scan: call blockOtherBrowsersDynamic() which
     *            queries the whole device and hides every non-Chrome
     *            browser it finds. Belt-and-suspenders.
     */
    private suspend fun autoBlock(context: Context, packageName: String) {
        // ── Gate: only act when we are Device Owner ───────────────────────────
        val deviceOwnerService = DeviceOwnerService(context)
        if (!deviceOwnerService.isDeviceOwner()) {
            Log.d(TAG, "⏭️  Not Device Owner — skipping auto-block for $packageName")
            return
        }

        val filteringService = ContentFilteringService(context)

        // ── PHASE 1: Immediate block (known browsers list) ────────────────────
        // setApplicationHidden() works by package name alone — no PM query needed.
        // This fires within milliseconds of install for any browser we know about.
        if (filteringService.isKnownBrowserPackage(packageName)) {
            Log.w(TAG, "🔒 Phase 1: known browser — hiding $packageName immediately")
            val hidden = filteringService.hideBrowserPackage(packageName)
            Log.i(TAG, "Phase 1 result for $packageName: hidden=$hidden")
            if (hidden) showBlockedNotification(context, packageName)
        } else {
            Log.d(TAG, "Phase 1: $packageName not in known list — proceeding to Phase 2")
        }

        // ── PHASE 2: Delayed PM query (unknown/new browsers) ─────────────────
        // Wait for Android to finish indexing the new app's intent filters.
        delay(PM_INDEX_DELAY_MS)

        val isBrowserByIntent = isPackageABrowser(context, packageName)
        Log.d(TAG, "Phase 2: isPackageABrowser($packageName) = $isBrowserByIntent")

        if (isBrowserByIntent) {
            val hidden = filteringService.hideBrowserPackage(packageName)
            Log.i(TAG, "Phase 2 result for $packageName: hidden=$hidden")
            if (hidden) showBlockedNotification(context, packageName)
        }

        // ── PHASE 3: Full re-scan ─────────────────────────────────────────────
        // Catch anything that slipped through Phases 1 and 2.
        // blockOtherBrowsersDynamic() queries ALL installed apps that can
        // handle web URLs and hides every one except Chrome.
        Log.d(TAG, "Phase 3: running full browser re-scan…")
        val result = filteringService.blockOtherBrowsersDynamic()
        Log.i(TAG, "Phase 3 complete: ${result.blocked} browser(s) blocked in re-scan")
    }

    /**
     * Asks PackageManager if [packageName] handles BROWSABLE web URLs.
     *
     * ⚠️  Only reliable ~3s after PACKAGE_ADDED fires.
     *      Call this after [PM_INDEX_DELAY_MS] has elapsed.
     */
    private fun isPackageABrowser(context: Context, packageName: String): Boolean {
        return try {
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.google.com")
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage(packageName)   // strictly target this package only
            }

            val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    webIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(
                    webIntent,
                    PackageManager.MATCH_ALL
                )
            }

            matches.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "isPackageABrowser check failed for $packageName: ${e.message}")
            false
        }
    }

    private fun showBlockedNotification(context: Context, packageName: String) {
        try {
            val appName = try {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            } catch (e: Exception) { packageName }

            val notification = NotificationCompat.Builder(
                context, AppConstants.CHANNEL_ID_VIOLATIONS
            )
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🛡️  Browser Auto-Blocked")
                .setContentText("$appName was installed and blocked automatically.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "$appName was detected and immediately hidden.\n\n" +
                                "Only Chrome (with SafeSearch + no incognito) is " +
                                "allowed during your commitment."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            nm.notify(NOTIF_ID_BASE + packageName.hashCode(), notification)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notification: ${e.message}")
        }
    }
}