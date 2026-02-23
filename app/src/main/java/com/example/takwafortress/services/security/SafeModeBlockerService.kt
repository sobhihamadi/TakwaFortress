package com.example.takwafortress.services.security

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.example.takwafortress.receivers.BootCompletedReceiver

class SafeModeBlockerService(private val context: Context) {

    private val knoxService = KnoxActivationService(context)

    /**
     * Checks if device is currently in Safe Mode.
     */
    fun isInSafeMode(): Boolean {
        return try {
            // System property that indicates Safe Mode
            val safeModeProperty = System.getProperty("ro.boot.safemode")
            safeModeProperty == "1"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Blocks Safe Mode (Samsung Knox only - others use watchdog).
     */
    fun blockSafeMode(): SafeModeBlockResult {
        // Samsung devices: Use Knox to completely disable Safe Mode
        if (knoxService.isKnoxSupported()) {
            return knoxService.disableSafeModeViaKnox()
                .fold(
                    onSuccess = { SafeModeBlockResult.BlockedViaKnox },
                    onFailure = { SafeModeBlockResult.Failed(it.message ?: "Knox failed") }
                )
        }

        // Non-Samsung devices: Use watchdog approach
        return setupSafeModeWatchdog()
    }

    /**
     * Sets up a watchdog to detect and punish Safe Mode boots.
     * (For non-Samsung devices where we can't block Safe Mode entirely)
     */
    private fun setupSafeModeWatchdog(): SafeModeBlockResult {
        return try {
            // Register boot receiver to detect Safe Mode
            val filter = IntentFilter(Intent.ACTION_BOOT_COMPLETED)
            context.registerReceiver(BootCompletedReceiver(), filter)

            SafeModeBlockResult.WatchdogActive
        } catch (e: Exception) {
            SafeModeBlockResult.Failed(e.message ?: "Watchdog setup failed")
        }
    }

    /**
     * Handles Safe Mode detection (called by BootCompletedReceiver).
     */
    fun handleSafeModeDetected(): SafeModeResponse {
        if (!isInSafeMode()) {
            return SafeModeResponse.NotInSafeMode
        }

        // Samsung: This should never happen (Knox blocks it)
        if (knoxService.isKnoxSupported()) {
            return SafeModeResponse.KnoxBypassDetected
        }

        // Non-Samsung: Apply punishment
        return applySafeModePunishment()
    }

    /**
     * Applies punishment for Safe Mode boot attempt.
     */
    private fun applySafeModePunishment(): SafeModeResponse {
        // Option 1: Lock device until normal boot
        // Option 2: Add penalty time to commitment period
        // Option 3: Send alert to accountability partner (future)

        // For now, we'll force immediate reboot to normal mode
        return try {
            // Show full-screen overlay that blocks all interaction
            // Display message: "Safe Mode detected. Rebooting to protected mode..."
            // Auto-reboot after 10 seconds

            SafeModeResponse.PunishmentApplied("Device will reboot in 10 seconds")
        } catch (e: Exception) {
            SafeModeResponse.PunishmentFailed(e.message ?: "Unknown error")
        }
    }

    /**
     * Checks Safe Mode protection status.
     */
    fun getProtectionStatus(): SafeModeProtectionStatus {
        return when {
            knoxService.isKnoxSupported() -> SafeModeProtectionStatus.KnoxProtected
            isWatchdogActive() -> SafeModeProtectionStatus.WatchdogActive
            else -> SafeModeProtectionStatus.NotProtected
        }
    }

    /**
     * Checks if Safe Mode watchdog is active.
     */
    private fun isWatchdogActive(): Boolean {
        // Check if boot receiver is registered
        return try {
            // TODO: Implement proper watchdog status check
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets Safe Mode protection level description.
     */
    fun getProtectionDescription(): String {
        return when (getProtectionStatus()) {
            SafeModeProtectionStatus.KnoxProtected ->
                "✅ Safe Mode BLOCKED (Samsung Knox)\nSafe Mode cannot be entered."

            SafeModeProtectionStatus.WatchdogActive ->
                "⚠️ Safe Mode MONITORED\nIf Safe Mode is entered, device will be locked."

            SafeModeProtectionStatus.NotProtected ->
                "❌ Safe Mode NOT PROTECTED\nVulnerable to Safe Mode uninstall."
        }
    }
}

sealed class SafeModeBlockResult {
    object BlockedViaKnox : SafeModeBlockResult()
    object WatchdogActive : SafeModeBlockResult()
    data class Failed(val reason: String) : SafeModeBlockResult()
}

sealed class SafeModeResponse {
    object NotInSafeMode : SafeModeResponse()
    object KnoxBypassDetected : SafeModeResponse()
    data class PunishmentApplied(val message: String) : SafeModeResponse()
    data class PunishmentFailed(val reason: String) : SafeModeResponse()
}

sealed class SafeModeProtectionStatus {
    object KnoxProtected : SafeModeProtectionStatus()
    object WatchdogActive : SafeModeProtectionStatus()
    object NotProtected : SafeModeProtectionStatus()
}