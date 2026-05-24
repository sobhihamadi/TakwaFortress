package com.example.takwafortress.receivers

import android.app.NotificationManager
import android.app.PendingIntent
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
import kotlinx.coroutines.launch

class PackageInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageInstallReceiver"
        private const val NOTIF_ID_BASE = 9000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val dataUri = intent.data

        if ((action == Intent.ACTION_PACKAGE_ADDED ||
                    action == Intent.ACTION_PACKAGE_REPLACED) && dataUri != null) {

            val packageName = dataUri.schemeSpecificPart

            // Never touch Chrome or our own app
            if (packageName == ContentFilteringService.CHROME_PACKAGE ||
                packageName == context.packageName) {
                return
            }

            Log.d(TAG, "📦 Package event: $action → $packageName")

            // goAsync() keeps the process alive while we do background work
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    handlePackageInstalled(context, packageName)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error handling install for $packageName", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun handlePackageInstalled(context: Context, packageName: String) {
        val deviceOwnerService = DeviceOwnerService(context)

        // ── Gate: only act when we are Device Owner ─────────────────────────
        // Device Owner status persists permanently — no need for the user to
        // re-open the app or re-tap "Activate Full Protection".
        if (!deviceOwnerService.isDeviceOwner()) {
            Log.d(TAG, "⏭️  Not Device Owner — skipping auto-block for $packageName")
            return
        }

        if (!isPackageABrowser(context, packageName)) {
            Log.d(TAG, "ℹ️  $packageName is not a browser — skipping")
            return
        }

        Log.w(TAG, "🌐 New browser detected: $packageName — auto-blocking…")

        val filteringService = ContentFilteringService(context)
        val hidden = filteringService.hideBrowserPackage(packageName)

        if (hidden) {
            Log.i(TAG, "✅ Auto-blocked browser: $packageName")
            showBlockedNotification(context, packageName)
        } else {
            Log.e(TAG, "❌ Failed to hide $packageName")
        }
    }

    /**
     * Checks whether [packageName] can handle web URLs, making it a browser.
     * Targets only that specific package so we don't accidentally catch other apps.
     */
    private fun isPackageABrowser(context: Context, packageName: String): Boolean {
        return try {
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.google.com")
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage(packageName)  // strictly target this package only
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

    /**
     * Posts a persistent notification so the user knows the browser was blocked.
     * Uses the existing CHANNEL_ID_VIOLATIONS channel created in TaqwaApplication.
     */
    private fun showBlockedNotification(context: Context, packageName: String) {
        try {
            val appName = getAppLabel(context, packageName) ?: packageName

            val notification = NotificationCompat.Builder(
                context,
                AppConstants.CHANNEL_ID_VIOLATIONS
            )
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🛡️  Browser Auto-Blocked")
                .setContentText("$appName was installed and blocked automatically.")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(
                            "$appName was detected and immediately hidden.\n\n" +
                                    "Only Chrome (with SafeSearch + no incognito) is allowed " +
                                    "during your commitment."
                        )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val notifManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Use packageName hashCode so each blocked app gets its own notification
            notifManager.notify(NOTIF_ID_BASE + packageName.hashCode(), notification)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notification: ${e.message}")
        }
    }

    private fun getAppLabel(context: Context, packageName: String): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            null
        }
    }
}