package com.example.takwafortress.services.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.example.takwafortress.R
import com.example.takwafortress.services.security.WirelessAdbResult
import com.example.takwafortress.services.security.WirelessAdbService
import com.example.takwafortress.ui.activities.DeviceOwnerSetupActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PairingCodeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PairingCodeA11y"
        private const val NOTIF_CHANNEL_ID = "takwa_pairing"
        private const val NOTIF_ID = 1001

        const val EXTRA_ACTIVATION_SUCCESS = "extra_activation_success"
        const val EXTRA_ACTIVATION_ERROR   = "extra_activation_error"

        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.samsung.android.settings",
            "com.miui.securitycenter",
            "com.huawei.systemmanager",
            "com.oneplus.settings"
        )

        private val POPUP_KEYWORDS = listOf(
            "wi-fi pairing code",
            "wi\u2011fi pairing code",
            "pair with device"
        )
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var wirelessAdbService: WirelessAdbService
    private var lastProcessedKey = ""
    private var isPairing = false

    override fun onCreate() {
        super.onCreate()
        wirelessAdbService = WirelessAdbService(applicationContext)
        createNotificationChannel()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lastProcessedKey = ""
        isPairing = false
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
            packageNames = null
        }
        Log.d(TAG, "✅ PairingCodeAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (isPairing) return
        val packageName = event.packageName?.toString() ?: return
        val isSettings = SETTINGS_PACKAGES.any { packageName.contains(it, ignoreCase = true) }
        if (!isSettings) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val rootNode = rootInActiveWindow ?: return
        try { scanForPairingPopup(rootNode) }
        catch (e: Exception) { Log.e(TAG, "Scan error: ${e.message}") }
        finally { rootNode.recycle() }
    }

    private fun scanForPairingPopup(root: AccessibilityNodeInfo) {
        val allText = mutableListOf<String>()
        collectAllText(root, allText)
        val fullText = allText.joinToString(" ").lowercase()
        val isPairingPopup = POPUP_KEYWORDS.any { fullText.contains(it, ignoreCase = true) }
        if (!isPairingPopup) return
        val code = extractPairingCode(allText) ?: return
        val port = extractPort(allText) ?: return
        val key = "$code:$port"
        if (key == lastProcessedKey) return
        lastProcessedKey = key
        Log.d(TAG, "✅ Detected code+port — starting pipeline")
        isPairing = true
        runPairingPipeline(code, port)
    }

    private fun runPairingPipeline(code: String, port: String) {
        serviceScope.launch {
            try {
                showProgressNotification()
                Log.d(TAG, "Step 1/2 — Pairing...")
                val pairResult = wirelessAdbService.pairDevice(code, port.toInt())
                when (pairResult) {
                    is WirelessAdbResult.PairingSuccessNeedConnection -> Log.d(TAG, "✅ Paired!")
                    is WirelessAdbResult.Failed -> { finishWithResult(false, pairResult.reason); return@launch }
                    else -> { finishWithResult(false, "Unexpected pairing result"); return@launch }
                }
                Log.d(TAG, "Step 2/2 — Setting Device Owner...")
                when (val execResult = wirelessAdbService.connectAndSetDeviceOwner()) {
                    is WirelessAdbResult.Success -> finishWithResult(true, null)
                    is WirelessAdbResult.Failed -> {
                        if (execResult.reason.contains("already", ignoreCase = true))
                            finishWithResult(true, null)
                        else
                            finishWithResult(false, execResult.reason)
                    }
                    is WirelessAdbResult.AccountsExist ->
                        finishWithResult(false, "Remove all accounts first:\nSettings → Accounts → Remove all")
                    else -> finishWithResult(false, "Unexpected result")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pipeline crashed: ${e.message}", e)
                finishWithResult(false, "Error: ${e.message}")
            }
        }
    }

    private fun finishWithResult(success: Boolean, error: String?) {
        isPairing = false
        if (!success) lastProcessedKey = ""
        showResultNotification(success, error ?: "Unknown error")
        launchActivityWithResult(success, error)
    }

    private fun launchActivityWithResult(success: Boolean, error: String?) {
        val intent = Intent(applicationContext, DeviceOwnerSetupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ACTIVATION_SUCCESS, success)
            if (error != null) putExtra(EXTRA_ACTIVATION_ERROR, error)
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(NOTIF_CHANNEL_ID, "Taqwa Pairing", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun showProgressNotification() {
        val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fortress_logo)
            .setContentTitle("⚡ Activating Taqwa Fortress…")
            .setContentText("Pairing code detected — activating in background")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notif)
    }

    private fun showResultNotification(success: Boolean, message: String) {
        val intent = Intent(applicationContext, DeviceOwnerSetupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ACTIVATION_SUCCESS, success)
            if (!success) putExtra(EXTRA_ACTIVATION_ERROR, message)
        }
        val pending = PendingIntent.getActivity(applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fortress_logo)
            .setContentTitle(if (success) "🎉 Device Owner Activated!" else "❌ Activation Failed")
            .setContentText(if (success) "Tap to continue" else message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notif)
    }

    private fun collectAllText(node: AccessibilityNodeInfo, result: MutableList<String>) {
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { result.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (!result.contains(it)) result.add(it)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child -> collectAllText(child, result); child.recycle() }
        }
    }

    private fun extractPairingCode(texts: List<String>): String? {
        val regex = Regex("\\b(\\d{6})\\b")
        for (text in texts) {
            val m = regex.find(text) ?: continue
            val num = m.groupValues[1].toLongOrNull() ?: continue
            if (num in 100000..999999) return m.groupValues[1]
        }
        return null
    }

    private fun extractPort(texts: List<String>): String? {
        val ipPort = Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:(\\d{4,5})")
        for (text in texts) { val m = ipPort.find(text) ?: continue; return m.groupValues[1] }
        val portLabel = Regex("(?i)port[:\\s]+(\\d{4,5})")
        for (text in texts) {
            val m = portLabel.find(text) ?: continue
            val p = m.groupValues[1].toIntOrNull() ?: continue
            if (p in 1024..65535) return p.toString()
        }
        val fiveDigit = Regex("\\b(\\d{5})\\b")
        for (text in texts) {
            for (m in fiveDigit.findAll(text)) {
                val p = m.groupValues[1].toIntOrNull() ?: continue
                if (p in 30000..65535) return p.toString()
            }
        }
        return null
    }

    override fun onInterrupt() {}
    override fun onDestroy() { super.onDestroy(); serviceScope.cancel() }
}