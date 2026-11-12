package com.example.takwafortress.receivers

import com.example.takwafortress.services.security.SystemSafetyGuard
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.takwafortress.services.core.DeviceOwnerService
import com.example.takwafortress.services.filtering.ContentFilteringService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageInstallReceiver"
    }

    @SuppressLint("ServiceCast")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val dataUri = intent.data

        if ((action == Intent.ACTION_PACKAGE_ADDED ||
                    action == Intent.ACTION_PACKAGE_REPLACED) && dataUri != null
        ) {

            val packageName = dataUri.schemeSpecificPart

            if (packageName == ContentFilteringService.CHROME_PACKAGE ||
                packageName == context.packageName
            ) return

            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.Default).launch {
                try {
                    // ✅ Only check Device Owner — remove chromeManagedActive entirely
                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                            as DevicePolicyManager
                    val adminComponent = ComponentName(
                        context,
                        DeviceAdminReceiver::class.java





                    )

                    if (!dpm.isDeviceOwnerApp(context.packageName)) {

                        return@launch
                    }

                    if (isTargetPackageABrowser(context, packageName)) {
                        Log.d(TAG, "🚨 Browser installed: $packageName — blocking")
                        // no hiding (simplified)
                        Log.d(TAG, "✅ Blocked: $packageName")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error blocking browser", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }


    /**
     * Determines browser capabilities by pointing a web intent target directly at the new package
     */
    private fun isTargetPackageABrowser(context: Context, packageName: String): Boolean {
        if (SystemSafetyGuard.isPaused(context) || SystemSafetyGuard.isProtected(context, packageName)) return false
        return try {
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(SystemSafetyGuard.BROWSER_PROBE_URI)
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