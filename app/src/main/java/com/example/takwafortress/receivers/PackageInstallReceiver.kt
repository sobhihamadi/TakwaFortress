package com.example.takwafortress.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
            Log.d(TAG, "📦 New package detected or updated: $packageName")

            // Run the scan asynchronously off the main thread
            CoroutineScope(Dispatchers.Default).launch {
                val filteringService = ContentFilteringService(context)

                // Only enforce if full protection is supposed to be active
                if (filteringService.getProtectionStatus().chromeManagedActive) {
                    Log.d(TAG, "Enforcing browser block rules post-installation...")
                    filteringService.blockOtherBrowsersDynamic()
                }
            }
        }
    }
}