package com.example.takwafortress.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.takwafortress.services.filtering.ContentFilteringService
import com.example.takwafortress.services.core.DeviceOwnerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageInstallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val dataUri = intent.data

        if ((action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REPLACED) && dataUri != null) {
            val packageName = dataUri.schemeSpecificPart

            // Bypass security evaluation for Chrome and your own monitoring application
            if (packageName == ContentFilteringService.CHROME_PACKAGE || packageName == context.packageName) {
                return
            }

            Log.d(TAG, "📦 Package modification event detected: $packageName")

            // ⚠️ CRITICAL: Instructs Android to keep the broadcast process awake for async work
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val deviceOwnerService = DeviceOwnerService(context)

                    // If the app holds Device Owner rights, enforce the trap instantly
                    if (deviceOwnerService.isDeviceOwner()) {

                        if (isTargetPackageABrowser(context, packageName)) {
                            Log.d(TAG, "🚨 Unauthorized browser detected in background: $packageName. Hiding application...")

                            val filteringService = ContentFilteringService(context)
                            val hidden = filteringService.hideBrowserPackage(packageName)

                            if (hidden) {
                                Log.d(TAG, "🔒 Success: $packageName is now invisible in the launcher.")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error analyzing background package attachment", e)
                } finally {
                    // ⚠️ CRITICAL: Release the thread back to the Android OS
                    pendingResult.finish()
                }
            }
        }
    }

    /**
     * Determines browser capabilities by matching a web intent route specifically against the incoming package
     */
    private fun isTargetPackageABrowser(context: Context, packageName: String): Boolean {
        return try {
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.google.com")
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage(packageName) // Targets only this exact newly installed package
            }

            val matchedActivities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    webIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(webIntent, PackageManager.MATCH_ALL)
            }

            matchedActivities.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}