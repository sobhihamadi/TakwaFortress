package com.example.takwafortress.services.security

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives the 6-digit pairing code the user typed in the notification
 * RemoteInput, then runs the full pair → connect → set-device-owner flow
 * on a background coroutine.
 *
 * Results are broadcast back to DeviceOwnerSetupActivity via a local Intent.
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotifReplyReceiver"

        /** Action sent back to DeviceOwnerSetupActivity with the result */
        const val ACTION_ADB_RESULT   = "com.example.takwafortress.ACTION_ADB_RESULT"
        const val EXTRA_SUCCESS       = "EXTRA_SUCCESS"
        const val EXTRA_ERROR_MESSAGE = "EXTRA_ERROR_MESSAGE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // ── Extract typed code from RemoteInput ──────────────────────────────
        val bundle = RemoteInput.getResultsFromIntent(intent) ?: run {
            Log.e(TAG, "No RemoteInput bundle in intent")
            return
        }

        val code = bundle.getCharSequence(AdbDiscoveryService.KEY_CODE_INPUT)
            ?.toString()
            ?.trim()
            ?: run {
                Log.e(TAG, "Pairing code was null or blank")
                return
            }

        val pairingPort = intent.getIntExtra("PAIRING_PORT", -1)
        val connectPort = intent.getIntExtra("CONNECT_PORT", -1)
        val deviceIp    = intent.getStringExtra("DEVICE_IP") ?: "127.0.0.1"

        Log.d(TAG, "Code received, pairingPort=$pairingPort connectPort=$connectPort ip=$deviceIp")

        // Dismiss the pairing notification immediately
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(AdbDiscoveryService.NOTIF_PAIRING)

        // ── Run ADB flow in background ───────────────────────────────────────
        CoroutineScope(Dispatchers.IO).launch {
            val service = WirelessAdbService(context)

            // Step 1 — Pair
            val pairResult = service.pairDevice(code, pairingPort)

            if (pairResult is WirelessAdbResult.Failed) {
                broadcastResult(context, success = false, error = pairResult.reason)
                return@launch
            }

            // Step 2 — Connect + set device owner
            val execResult = service.connectAndSetDeviceOwner()

            when (execResult) {
                is WirelessAdbResult.Success -> {
                    broadcastResult(context, success = true)
                }
                is WirelessAdbResult.AccountsExist -> {
                    broadcastResult(
                        context, success = false,
                        error = "❌ Accounts still exist\n\nRemove ALL accounts from Settings → Accounts, then try again."
                    )
                }
                is WirelessAdbResult.Failed -> {
                    // "already set" counts as success
                    if (execResult.reason.contains("already", ignoreCase = true)) {
                        broadcastResult(context, success = true)
                    } else {
                        broadcastResult(context, success = false, error = execResult.reason)
                    }
                }
                else -> broadcastResult(context, success = false, error = "Unexpected result")
            }
        }
    }

    private fun broadcastResult(context: Context, success: Boolean, error: String = "") {
        val intent = Intent(ACTION_ADB_RESULT).apply {
            setPackage(context.packageName)   // explicit — required on Android 14+
            putExtra(EXTRA_SUCCESS, success)
            putExtra(EXTRA_ERROR_MESSAGE, error)
        }
        context.sendBroadcast(intent)
        Log.d(TAG, "Result broadcast: success=$success error=$error")
    }
}