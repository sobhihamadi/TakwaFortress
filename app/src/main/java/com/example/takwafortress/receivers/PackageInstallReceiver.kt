package com.example.takwafortress.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        // Wait for PackageManager to finish indexing the new app's intent filters.
        // PACKAGE_ADDED fires before PM is ready — 3s is enough on all tested devices.
        private const val PM_INDEX_DELAY_MS = 3_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action  = intent.action
        val dataUri = intent.data

        if ((action != Intent.ACTION_PACKAGE_ADDED &&
                    action != Intent.ACTION_PACKAGE_REPLACED) || dataUri == null) return

        val packageName = dataUri.schemeSpecificPart ?: return

        // Never touch Chrome or ourselves
        if (packageName == ContentFilteringService.CHROME_PACKAGE ||
            packageName == context.packageName) return

        Log.d(TAG, "📦 Package event: $action → $packageName")

        // goAsync() keeps the receiver process alive while we do background work
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

    private suspend fun autoBlock(context: Context, packageName: String) {
        // Gate: only act when we are Device Owner
        if (!DeviceOwnerService(context).isDeviceOwner()) {
            Log.d(TAG, "⏭️ Not Device Owner — skipping $packageName")
            return
        }

        val filteringService = ContentFilteringService(context)

        // ── PHASE 1: Immediate (t = 0ms) ──────────────────────────────────────
        // setApplicationHidden() needs only the package name string — no PM query.
        // Works instantly for any browser already in the hardcoded list.
        if (filteringService.isKnownBrowserPackage(packageName)) {
            Log.w(TAG, "🔒 Phase 1: known browser — hiding $packageName immediately")
            val hidden = filteringService.hideBrowserPackage(packageName)
            Log.i(TAG, "Phase 1 result: hidden=$hidden")
            if (hidden) showBlockedNotification(context, packageName)
        } else {
            Log.d(TAG, "Phase 1: $packageName not in known list — skipping to Phase 2")
        }

        // ── PHASE 2 + 3: Full protection (t = 3000ms) ─────────────────────────
        // Wait for Android to finish indexing the new app's intent filters,
        // then run the EXACT same code the "Activate Full Protection" button runs.
        // This catches unknown/new browsers that aren't in the hardcoded list.
        delay(PM_INDEX_DELAY_MS)
        Log.d(TAG, "Phase 2+3: running full protection (same as button)…")

        val result = filteringService.activateFullProtection()
        Log.i(TAG, "Phase 2+3 complete: $result")
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
                        "$appName was detected and hidden immediately.\n\n" +
                                "Only Chrome (with SafeSearch + no incognito) is allowed " +
                                "during your commitment."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIF_ID_BASE + packageName.hashCode(), notification)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notification: ${e.message}")
        }
    }
}