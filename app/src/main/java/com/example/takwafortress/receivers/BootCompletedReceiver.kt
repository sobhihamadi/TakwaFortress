package com.example.takwafortress.receivers

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.takwafortress.util.constants.AppConstants

/**
 * Boot Completed Receiver
 *
 * RULES — this file must follow these strictly:
 * 1. Do as little as possible during onReceive
 * 2. Delay ALL service starts by minimum 15 seconds
 * 3. Never start more than one service at a time
 * 4. Never run heavy work directly in onReceive
 * 5. Never detect or react to safe mode here
 *    — safe mode detection caused the boot loop
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "${AppConstants.LOG_TAG}_BootReceiver"
        private const val BOOT_DELAY_MS = 15_000L // 15 seconds — gives Android time to fully initialize
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Boot completed received — scheduling delayed start")

        // ✅ RULE: Never do heavy work here
        // ✅ RULE: Never detect safe mode here — this caused the boot loop
        // ✅ RULE: Delay everything — Android needs time to initialize fully

        Handler(Looper.getMainLooper()).postDelayed({
            handleBootDelayed(context)
        }, BOOT_DELAY_MS)
    }

    private fun handleBootDelayed(context: Context) {
        try {
            Log.i(TAG, "Boot delay complete — checking device state")

            // ✅ RULE: Only start services if Device Owner is active
            // If user never completed setup, do nothing at all
            if (!isDeviceOwnerActive(context)) {
                Log.i(TAG, "Device Owner not active — skipping all service starts")
                return
            }

            Log.i(TAG, "Device Owner active — starting protection services")

            // ✅ RULE: Start ONE service only
            // ContentFilteringService is responsible for chaining
            // other services — do not start multiple here
            startProtectionService(context)

        } catch (e: Exception) {
            // ✅ RULE: Never crash during boot
            // Log the error and exit gracefully
            Log.e(TAG, "Boot handler failed — device will work normally: ${e.message}")
        }
    }

    private fun isDeviceOwnerActive(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                    as DevicePolicyManager
            val isOwner = dpm.isDeviceOwnerApp(context.packageName)
            Log.i(TAG, "Device Owner check: $isOwner")
            isOwner
        } catch (e: Exception) {
            Log.e(TAG, "Device Owner check failed: ${e.message}")
            false // safe default — do nothing if check fails
        }
    }

    private fun startProtectionService(context: Context) {
        try {
            // Import your actual ContentFilteringService here
            val serviceIntent = Intent().apply {
                setClassName(
                    context.packageName,
                    "${context.packageName}.services.filtering.ContentFilteringService"
                )
                putExtra("started_from_boot", true)
            }
            context.startForegroundService(serviceIntent)
            Log.i(TAG, "ContentFilteringService start requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ContentFilteringService: ${e.message}")
            // Do not crash — protection simply won't start this boot
            // User will see protection inactive on dashboard
        }
    }
}