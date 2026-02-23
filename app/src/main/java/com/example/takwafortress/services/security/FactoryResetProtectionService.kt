
package com.example.takwafortress.services.security


import android.content.Context
import com.example.takwafortress.services.core.DeviceOwnerService

class FactoryResetProtectionService(private val context: Context) {

    private val deviceOwnerService = DeviceOwnerService(context)

    /**
     * Enables Factory Reset Protection (FRP).
     * Disables Settings → Reset options.
     */
//    fun enableFactoryResetProtection(): FrpResult {
//        if (!deviceOwnerService.isDeviceOwner()) {
//            return FrpResult.DeviceOwnerRequired
//        }
//
//        return deviceOwnerService.disableFactoryReset()
//            .fold(
//                onSuccess = { FrpResult.Enabled },
//                onFailure = { FrpResult.Failed(it.message ?: "Unknown error") }
//            )
//    }

    /**
     * Checks if factory reset is blocked.
     */
    fun isFactoryResetBlocked(): Boolean {
        // Check if Device Owner restriction is active
        return deviceOwnerService.isDeviceOwner()
    }

    /**
     * Gets Factory Reset Protection status.
     */
    fun getFrpStatus(): FrpStatus {
        return when {
            isFactoryResetBlocked() -> FrpStatus.Protected
            deviceOwnerService.isDeviceOwner() -> FrpStatus.CanBeEnabled
            else -> FrpStatus.NotAvailable
        }
    }

    /**
     * Provides user-friendly explanation of FRP.
     */
    fun getFrpExplanation(): String {
        return """
            🔒 Factory Reset Protection (FRP)
            
            WHAT IT DOES:
            ✅ Disables "Reset" in Settings
            ⚠️ Hardware button reset still works
            
            WHAT HAPPENS ON HARDWARE RESET:
            1. Device wipes and restarts
            2. Shows "This device is protected"
            3. Requires Google account login
            4. Device is locked until verified
            
            RESULT:
            - You cannot use the device without Google account
            - Taqwa license is revoked (must pay again)
            - Your commitment data is safe
            
            VERDICT:
            Factory reset is possible, but costly and inconvenient.
        """.trimIndent()
    }

    /**
     * Gets recovery instructions (what user sees after factory reset).
     */
    fun getPostResetInstructions(): String {
        return """
            Your device was factory reset.
            
            Your Taqwa license has been revoked.
            To reactivate Taqwa:
            
            1. Purchase new license ($17)
            2. Reinstall Taqwa app
            3. Activate with new license key
            
            Your previous commitment:
            - Duration: [Stored in cloud]
            - Progress: Lost due to reset
            
            This is considered a relapse.
            Start fresh with a new commitment.
        """.trimIndent()
    }

    /**
     * Checks if device has FRP-compatible Google account.
     */
    fun hasRecoveryAccount(): Boolean {
        // TODO: Check if Google account is configured
        return false
    }

    /**
     * Gets the psychological deterrent message.
     */
    fun getDeterrentMessage(): String {
        return """
            ⚠️ FACTORY RESET WARNING ⚠️
            
            If you factory reset this device:
            
            💰 You lose your $17 payment
            📱 Device will be locked until you login
            🔄 You must buy Taqwa again ($17)
            📊 All progress is lost
            
            Is it worth it?
            
            Instead:
            ✅ Wait ${getRemainingDaysMessage()}
            ✅ Complete your commitment
            ✅ Unlock naturally (free)
            ✅ Feel proud of yourself
            
            The choice is yours.
        """.trimIndent()
    }

    /**
     * Gets remaining days message (requires fortress policy).
     */
    private fun getRemainingDaysMessage(): String {
        // TODO: Get from FortressActivationService
        return "[X] more days"
    }

    /**
     * Simulates factory reset (for testing purposes only).
     */
    fun simulateFactoryReset(): FrpSimulationResult {
        return FrpSimulationResult.Success(
            message = """
                Simulation: Factory reset completed
                - All data wiped
                - Taqwa license revoked
                - Device shows Google account verification
                - User must pay $17 to reactivate
            """.trimIndent()
        )
    }
}

sealed class FrpResult {
    object Enabled : FrpResult()
    object DeviceOwnerRequired : FrpResult()
    data class Failed(val reason: String) : FrpResult()
}

sealed class FrpStatus {
    object Protected : FrpStatus()
    object CanBeEnabled : FrpStatus()
    object NotAvailable : FrpStatus()
}

sealed class FrpSimulationResult {
    data class Success(val message: String) : FrpSimulationResult()
}