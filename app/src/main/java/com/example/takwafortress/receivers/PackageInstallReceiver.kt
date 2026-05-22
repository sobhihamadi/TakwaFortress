package com.example.takwafortress.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.takwafortress.services.filtering.ContentFilteringService
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

            // Bypass security loops for Chrome and your own monitoring application
            if (packageName == ContentFilteringService.CHROME_PACKAGE || packageName == context.packageName) {
                return
            }

            Log.d(TAG, "📦 Package change detected: $packageName")

            // ⚠️ CRITICAL: Instructs Android to keep the process alive for async execution
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val filteringService = ContentFilteringService(context)

                    // Only enforce rules if content filtering is currently activated
                    if (filteringService.getProtectionStatus().chromeManagedActive) {

                        if (isTargetPackageABrowser(context, packageName)) {
                            Log.d(TAG, "🚨 Browser properties confirmed for: $packageName. Hiding application...")
                            val hidden = filteringService.hideBrowserPackage(packageName)
                            if (hidden) {
                                Log.d(TAG, "🔒 Target package successfully hidden from launcher.")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error analyzing package installation details", e)
                } finally {
                    // ⚠️ CRITICAL: Signals the OS that processing is done and resources can be recycled
                    pendingResult.finish()
                }
            }
        }
    }

    /**
     * Determines browser capabilities by pointing a web intent target directly at the new package
     */
    private fun isTargetPackageABrowser(context: Context, packageName: String): Boolean {
        return try {
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.google.com")
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage(packageName) // Strictly targets only this specific app
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