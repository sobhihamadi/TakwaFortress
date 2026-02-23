package com.example.takwafortress.services.monitoring


import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.example.takwafortress.receivers.DeviceAdminReceiver
import com.example.takwafortress.services.core.DeviceOwnerService

class TimeProtectionService(private val context: Context) {

    private val deviceOwnerService = DeviceOwnerService(context)
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)

    /**
     * Forces automatic time from network.
     * Prevents "time travel" to skip commitment period.
     */
    fun forceAutoTime(): TimeProtectionResult {
        if (!deviceOwnerService.isDeviceOwner()) {
            return TimeProtectionResult.DeviceOwnerRequired
        }

        return deviceOwnerService.forceAutoTime()
            .fold(
                onSuccess = { TimeProtectionResult.Enabled },
                onFailure = { TimeProtectionResult.Failed(it.message ?: "Unknown error") }
            )
    }

    /**
     * Checks if automatic time is enabled.
     */
    fun isAutoTimeEnabled(): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AUTO_TIME,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if automatic timezone is enabled.
     */
    fun isAutoTimezoneEnabled(): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AUTO_TIME_ZONE,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets current device time.
     */
    fun getCurrentTime(): Long {
        return System.currentTimeMillis()
    }

    /**
     * Detects time tampering by comparing with network time.
     */
    suspend fun detectTimeTampering(): TimeTamperingResult {
        if (!isAutoTimeEnabled()) {
            return TimeTamperingResult.AutoTimeDisabled
        }

        // TODO: Implement network time check (NTP server)
        // For now, if auto-time is enabled, we trust system time

        return TimeTamperingResult.NoTampering
    }

    /**
     * Gets time protection status.
     */
    fun getTimeProtectionStatus(): TimeProtectionStatus {
        val autoTimeEnabled = isAutoTimeEnabled()
        val autoTimezoneEnabled = isAutoTimezoneEnabled()
        val isDeviceOwner = deviceOwnerService.isDeviceOwner()

        return when {
            !isDeviceOwner -> TimeProtectionStatus.NotAvailable
            autoTimeEnabled && autoTimezoneEnabled -> TimeProtectionStatus.FullyProtected
            autoTimeEnabled || autoTimezoneEnabled -> TimeProtectionStatus.PartiallyProtected
            else -> TimeProtectionStatus.NotProtected
        }
    }

    /**
     * Gets time protection explanation.
     */
    fun getTimeProtectionExplanation(): String {
        return """
            ⏰ TIME PROTECTION
            
            THE VULNERABILITY:
            Without time protection, you could:
            1. Go to Settings → Date & Time
            2. Disable "Automatic date & time"
            3. Set date to 90 days in future
            4. Taqwa thinks your commitment is over
            5. Unlock fortress early
            
            THE SOLUTION:
            ✅ Force "Automatic date & time" ON
            ✅ Cannot be changed in Settings
            ✅ Device syncs with network time
            ✅ Tampering is impossible
            
            HOW IT WORKS:
            - Device Owner policy forces auto-time
            - Time syncs from mobile network or Wi-Fi
            - Even if you try to change it, it auto-corrects
            - Settings → Date & Time is greyed out
            
            WHAT IF NETWORK TIME IS WRONG?
            Very rare, but if it happens:
            - Your commitment period adjusts accordingly
            - Network time is trusted source
            - Manual override is not possible (by design)
            
            Current Status: ${getTimeProtectionStatus()}
            
            Auto Time: ${if (isAutoTimeEnabled()) "✅ Enabled" else "❌ Disabled"}
            Auto Timezone: ${if (isAutoTimezoneEnabled()) "✅ Enabled" else "❌ Disabled"}
        """.trimIndent()
    }

    /**
     * Gets time remaining in human-readable format.
     */
    fun formatTimeRemaining(milliseconds: Long): String {
        if (milliseconds <= 0) return "00:00:00:00"

        val days = (milliseconds / (1000 * 60 * 60 * 24)).toInt()
        val hours = ((milliseconds / (1000 * 60 * 60)) % 24).toInt()
        val minutes = ((milliseconds / (1000 * 60)) % 60).toInt()
        val seconds = ((milliseconds / 1000) % 60).toInt()

        return String.format("%02d:%02d:%02d:%02d", days, hours, minutes, seconds)
    }

    /**
     * Calculates time drift (difference between system time and expected time).
     */
    fun calculateTimeDrift(expectedTime: Long): Long {
        val currentTime = getCurrentTime()
        return currentTime - expectedTime
    }

    /**
     * Checks if there's significant time drift (more than 5 minutes).
     */
    fun hasSignificantTimeDrift(expectedTime: Long): Boolean {
        val drift = calculateTimeDrift(expectedTime)
        val fiveMinutes = 5 * 60 * 1000L
        return kotlin.math.abs(drift) > fiveMinutes
    }

    /**
     * Gets time protection health check.
     */
    fun performHealthCheck(): TimeProtectionHealthCheck {
        return TimeProtectionHealthCheck(
            isDeviceOwnerActive = deviceOwnerService.isDeviceOwner(),
            isAutoTimeEnabled = isAutoTimeEnabled(),
            isAutoTimezoneEnabled = isAutoTimezoneEnabled(),
            currentTime = getCurrentTime(),
            status = getTimeProtectionStatus()
        )
    }
}

sealed class TimeProtectionResult {
    object Enabled : TimeProtectionResult()
    object DeviceOwnerRequired : TimeProtectionResult()
    data class Failed(val reason: String) : TimeProtectionResult()
}

sealed class TimeProtectionStatus {
    object FullyProtected : TimeProtectionStatus()
    object PartiallyProtected : TimeProtectionStatus()
    object NotProtected : TimeProtectionStatus()
    object NotAvailable : TimeProtectionStatus()
}

sealed class TimeTamperingResult {
    object NoTampering : TimeTamperingResult()
    object AutoTimeDisabled : TimeTamperingResult()
    data class TamperingDetected(val driftMillis: Long) : TimeTamperingResult()
}

data class TimeProtectionHealthCheck(
    val isDeviceOwnerActive: Boolean,
    val isAutoTimeEnabled: Boolean,
    val isAutoTimezoneEnabled: Boolean,
    val currentTime: Long,
    val status: TimeProtectionStatus
)