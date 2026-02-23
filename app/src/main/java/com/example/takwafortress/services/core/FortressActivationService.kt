package com.example.takwafortress.services.core

import android.content.Context
import android.util.Log
import com.example.takwafortress.model.builders.FortressPolicyBuilder
import com.example.takwafortress.model.builders.IdentifierFortressPolicyBuilder
import com.example.takwafortress.model.entities.IdentifierFortressPolicy
import com.example.takwafortress.model.enums.ActivationMethod
import com.example.takwafortress.model.enums.CommitmentPlan
import com.example.takwafortress.model.enums.FortressState
import com.example.takwafortress.repository.implementations.LocalFortressPolicyRepository
import com.example.takwafortress.services.filtering.ContentFilteringService
import com.example.takwafortress.services.filtering.ContentFilterResult
import com.example.takwafortress.util.constants.BlockedPackages
import java.util.UUID

/**
 * Fortress Activation Service
 *
 * Handles the complete fortress activation process:
 * 1. Validates Device Owner status
 * 2. Creates fortress policy
 * 3. Applies Device Owner restrictions
 * 4. Activates content filtering (3-layer protection)
 * 5. Manages fortress lifecycle
 */
class FortressActivationService(private val context: Context) {

    companion object {
        private const val TAG = "FortressActivation"
    }

    private val fortressPolicyRepository = LocalFortressPolicyRepository(context)
    private val deviceOwnerService = DeviceOwnerService(context)
    private val contentFilteringService = ContentFilteringService(context)

    /**
     * Activates the fortress with the selected commitment plan.
     * This is the main "lock" function that seals the device.
     */
    suspend fun activateFortress(
        commitmentPlan: CommitmentPlan,
        activationMethod: ActivationMethod
    ): FortressActivationResult {

        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "FORTRESS ACTIVATION STARTED")
        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "Plan: ${commitmentPlan.name}")
        Log.i(TAG, "Method: ${activationMethod.name}")

        // ═══════════════════════════════════════
        // Step 1: Verify Device Owner is active
        // ═══════════════════════════════════════
        if (!deviceOwnerService.isDeviceOwner()) {
            Log.e(TAG, "❌ Device Owner not active")
            return FortressActivationResult.DeviceOwnerNotActive
        }
        Log.i(TAG, "✅ Device Owner verified")

        // ═══════════════════════════════════════
        // Step 2: Check if fortress is already active
        // ═══════════════════════════════════════
        if (fortressPolicyRepository.hasActivePolicy()) {
            Log.w(TAG, "⚠️ Fortress already active")
            return FortressActivationResult.AlreadyActive
        }

        // ═══════════════════════════════════════
        // Step 3: Create fortress policy
        // ═══════════════════════════════════════
        val now = System.currentTimeMillis()
        val expiryTime = now + commitmentPlan.getDurationMillis()

        Log.i(TAG, "Creating fortress policy...")
        Log.i(TAG, "Activation: $now")
        Log.i(TAG, "Expiry: $expiryTime")
        Log.i(TAG, "Duration: ${commitmentPlan.getDurationMillis() / (1000 * 60 * 60 * 24)} days")

        val fortressPolicy = FortressPolicyBuilder.newBuilder()
            .setCommitmentPlan(commitmentPlan)
            .setActivationTimestamp(now)
            .setExpiryTimestamp(expiryTime)
            .setIsDeviceOwnerActive(true)
            .setActivationMethod(activationMethod)
            .setBlockedApps(BlockedPackages.ALL_BLOCKED)
            .setIsDnsForced(false) // Will be set by ContentFilteringService
            .setIsSafeModeDisabled(false) // Will be set by SafeModeBlockerService
            .setIsFactoryResetBlocked(false) // Will be set later
            .setIsTimeProtectionActive(false) // Will be set later
            .setCurrentState(FortressState.ACTIVATING)
            .build()

        val identifierFortressPolicy = IdentifierFortressPolicyBuilder.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setFortressPolicy(fortressPolicy)
            .build()

        Log.i(TAG, "✅ Policy created with ID: ${identifierFortressPolicy.getId()}")

        // ═══════════════════════════════════════
        // Step 4: Apply Device Owner restrictions
        // ═══════════════════════════════════════
        Log.i(TAG, "Applying Device Owner restrictions...")
        val restrictionsResult = applyDeviceOwnerRestrictions(identifierFortressPolicy)

        if (restrictionsResult is RestrictionResult.Failure) {
            Log.e(TAG, "❌ Restrictions failed:")
            restrictionsResult.errors.forEach { Log.e(TAG, "  - $it") }
            return FortressActivationResult.RestrictionsFailed(restrictionsResult.errors)
        }
        Log.i(TAG, "✅ All restrictions applied successfully")

        // ═══════════════════════════════════════
        // Step 5: Update fortress state to ACTIVE
        // ═══════════════════════════════════════
        fortressPolicyRepository.updateFortressState(FortressState.ACTIVE)
        Log.i(TAG, "✅ Fortress state set to ACTIVE")

        // ═══════════════════════════════════════
        // Step 6: Save active policy
        // ═══════════════════════════════════════
        fortressPolicyRepository.setActivePolicy(identifierFortressPolicy)
        Log.i(TAG, "✅ Active policy saved")

        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "🎉 FORTRESS ACTIVATION SUCCESSFUL!")
        Log.i(TAG, "═══════════════════════════════════════")

        return FortressActivationResult.Success(identifierFortressPolicy)
    }

    /**
     * Applies all Device Owner restrictions and content filtering.
     *
     * Protection layers:
     * 1. Block uninstall of Taqwa app
     * 2. Hide nuclear blacklist apps (Telegram, Reddit, X, Discord)
     * 3. Suspend other browsers (grey them out)
     * 4. Force automatic time
     * 5. Activate 3-layer content filtering:
     *    - DNS filtering (CleanBrowsing)
     *    - Chrome SafeSearch enforcement
     *    - Browser blocking
     */
    private suspend fun applyDeviceOwnerRestrictions(
        policy: IdentifierFortressPolicy
    ): RestrictionResult {
        val errors = mutableListOf<String>()

        // ═══════════════════════════════════════
        // 1. Block uninstallation of Taqwa itself
        // ═══════════════════════════════════════
        Log.i(TAG, "Blocking uninstall of Taqwa...")
        deviceOwnerService.blockUninstall().onFailure {
            val error = "Failed to block uninstall: ${it.message}"
            Log.e(TAG, "❌ $error")
            errors.add(error)
        }.onSuccess {
            Log.i(TAG, "✅ Uninstall blocked")
        }

        // ═══════════════════════════════════════
        // 2. Hide nuclear blacklist apps
        // ═══════════════════════════════════════
        val nuclearApps = policy.getBlockedApps().filter {
            it in BlockedPackages.NUCLEAR_BLACKLIST
        }

        if (nuclearApps.isNotEmpty()) {
            Log.i(TAG, "Hiding ${nuclearApps.size} nuclear apps...")
            deviceOwnerService.hideApps(nuclearApps).onFailure {
                val error = "Failed to hide nuclear apps: ${it.message}"
                Log.e(TAG, "❌ $error")
                errors.add(error)
            }.onSuccess {
                Log.i(TAG, "✅ Nuclear apps hidden: ${nuclearApps.joinToString()}")
            }
        }

        // ═══════════════════════════════════════
        // 3. Suspend other browsers
        // ═══════════════════════════════════════
        val browsers = policy.getBlockedApps().filter {
            it in BlockedPackages.BROWSERS && it !in BlockedPackages.NUCLEAR_BLACKLIST
        }

        if (browsers.isNotEmpty()) {
            Log.i(TAG, "Suspending ${browsers.size} browsers...")
            deviceOwnerService.suspendApps(browsers).onFailure {
                val error = "Failed to suspend browsers: ${it.message}"
                Log.e(TAG, "❌ $error")
                errors.add(error)
            }.onSuccess {
                Log.i(TAG, "✅ Browsers suspended: ${browsers.joinToString()}")
            }
        }

        // ═══════════════════════════════════════
        // 4. Force automatic time
        // ═══════════════════════════════════════
        Log.i(TAG, "Forcing automatic time...")
        deviceOwnerService.forceAutoTime().onFailure {
            val error = "Failed to force auto time: ${it.message}"
            Log.e(TAG, "❌ $error")
            errors.add(error)
        }.onSuccess {
            Log.i(TAG, "✅ Automatic time enforced")
        }

        // ═══════════════════════════════════════
        // 5. Activate 3-Layer Content Filtering
        // ═══════════════════════════════════════
        Log.i(TAG, "Activating 3-layer content filtering...")
        Log.i(TAG, "  Layer 1: DNS filtering (CleanBrowsing)")
        Log.i(TAG, "  Layer 2: Chrome SafeSearch enforcement")
        Log.i(TAG, "  Layer 3: Browser blocking")

        val contentFilterResult = contentFilteringService.activateFullProtection()

        when (contentFilterResult) {
            is ContentFilterResult.Success -> {
                Log.i(TAG, "✅ Content filtering activated successfully")
                Log.i(TAG, "Details: ${contentFilterResult.details}")

                // Update policy to reflect DNS is now forced
                // Note: Since updateDnsStatus doesn't exist in the repository,
                // we skip this step. The policy's isDnsForced flag will be
                // managed through the policy builder in future updates.
            }

            is ContentFilterResult.DeviceOwnerRequired -> {
                val error = "Content filtering requires Device Owner"
                Log.e(TAG, "❌ $error")
                errors.add(error)
            }

            is ContentFilterResult.Failed -> {
                val error = "Content filtering failed: ${contentFilterResult.reason}"
                Log.e(TAG, "❌ $error")
                errors.add(error)
            }
        }

        // ═══════════════════════════════════════
        // Return result
        // ═══════════════════════════════════════
        return if (errors.isEmpty()) {
            Log.i(TAG, "✅ All restrictions applied successfully")
            RestrictionResult.Success
        } else {
            Log.e(TAG, "⚠️ Some restrictions failed (${errors.size} errors)")
            RestrictionResult.Failure(errors)
        }
    }

    /**
     * Deactivates the fortress (only allowed if period has expired).
     */
    suspend fun deactivateFortress(): FortressDeactivationResult {
        Log.i(TAG, "Attempting fortress deactivation...")

        val activePolicy = fortressPolicyRepository.getActivePolicy()
            ?: return FortressDeactivationResult.NoActivePolicy

        // Check if unlock is eligible (period expired)
        if (!activePolicy.isUnlockEligible()) {
            val remainingDays = activePolicy.getRemainingDays()
            Log.w(TAG, "❌ Period not expired. Remaining: $remainingDays days")
            return FortressDeactivationResult.PeriodNotExpired(
                remainingDays = remainingDays
            )
        }

        // Update state to UNLOCKABLE (allows user to choose to deactivate)
        fortressPolicyRepository.updateFortressState(FortressState.UNLOCKABLE)
        Log.i(TAG, "✅ Fortress marked as UNLOCKABLE")

        return FortressDeactivationResult.Success
    }

    /**
     * Checks if fortress can be unlocked.
     */
    suspend fun canUnlock(): Boolean {
        val activePolicy = fortressPolicyRepository.getActivePolicy()
        val canUnlock = activePolicy?.isUnlockEligible() == true

        if (canUnlock) {
            Log.i(TAG, "✅ Fortress can be unlocked")
        } else {
            Log.i(TAG, "❌ Fortress cannot be unlocked yet")
        }

        return canUnlock
    }

    /**
     * Gets the current fortress status.
     */
    suspend fun getFortressStatus(): FortressStatus {
        val activePolicy = fortressPolicyRepository.getActivePolicy()
            ?: return FortressStatus.Inactive

        return FortressStatus.Active(
            policy = activePolicy,
            remainingDays = activePolicy.getRemainingDays(),
            progressPercentage = activePolicy.getProgressPercentage(),
            protectionScore = activePolicy.getProtectionScore()
        )
    }

    /**
     * Gets remaining time details.
     */
    suspend fun getRemainingTime(): RemainingTime? {
        val activePolicy = fortressPolicyRepository.getActivePolicy() ?: return null

        val remainingMillis = activePolicy.getExpiryTimestamp() - System.currentTimeMillis()

        if (remainingMillis <= 0) {
            return RemainingTime(0, 0, 0, 0)
        }

        val days = (remainingMillis / (1000 * 60 * 60 * 24)).toInt()
        val hours = ((remainingMillis / (1000 * 60 * 60)) % 24).toInt()
        val minutes = ((remainingMillis / (1000 * 60)) % 60).toInt()
        val seconds = ((remainingMillis / 1000) % 60).toInt()

        return RemainingTime(days, hours, minutes, seconds)
    }

    /**
     * Gets content filtering status report.
     * This provides a formatted string showing all protection layers.
     */
    fun getContentFilteringStatus(): String {
        return contentFilteringService.getStatusReport()
    }

    /**
     * Gets detailed protection status.
     * Useful for dashboard display.
     */
//    fun getProtectionStatus(): ProtectionStatus {
//        val contentStatus = contentFilteringService.getProtectionStatus()
//
//        return ProtectionStatus(
//            isDnsActive = contentStatus.dnsFilterActive,
//            isChromeManaged = contentStatus.chromeManagedActive,
//            areBrowsersBlocked = contentStatus.browsersBlocked,
//            isDeviceOwnerActive = deviceOwnerService.isDeviceOwner()
//        )
//    }
}

// ═══════════════════════════════════════════════════════════════
// Result Classes
// ═══════════════════════════════════════════════════════════════

sealed class FortressActivationResult {
    data class Success(val policy: IdentifierFortressPolicy) : FortressActivationResult()
    object DeviceOwnerNotActive : FortressActivationResult()
    object AlreadyActive : FortressActivationResult()
    data class RestrictionsFailed(val errors: List<String>) : FortressActivationResult()
}

sealed class FortressDeactivationResult {
    object Success : FortressDeactivationResult()
    object NoActivePolicy : FortressDeactivationResult()
    data class PeriodNotExpired(val remainingDays: Int) : FortressDeactivationResult()
}

sealed class FortressStatus {
    object Inactive : FortressStatus()
    data class Active(
        val policy: IdentifierFortressPolicy,
        val remainingDays: Int,
        val progressPercentage: Float,
        val protectionScore: Int
    ) : FortressStatus()
}

sealed class RestrictionResult {
    object Success : RestrictionResult()
    data class Failure(val errors: List<String>) : RestrictionResult()
}

data class RemainingTime(
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int
)

/**
 * Comprehensive protection status.
 * Used by dashboard to show all protection layers.
 */
data class ProtectionStatus(
    val isDnsActive: Boolean,
    val isChromeManaged: Boolean,
    val areBrowsersBlocked: Boolean,
    val isDeviceOwnerActive: Boolean
) {
    /**
     * Overall protection score (0-100).
     */
    fun getScore(): Int {
        var score = 0
        if (isDnsActive) score += 25
        if (isChromeManaged) score += 25
        if (areBrowsersBlocked) score += 25
        if (isDeviceOwnerActive) score += 25
        return score
    }

    /**
     * Formatted status report.
     */
    fun getReport(): String {
        return buildString {
            appendLine("🛡️ PROTECTION STATUS")
            appendLine()
            appendLine("Overall Score: ${getScore()}/100")
            appendLine()
            appendLine("Layer 1 - DNS Filter: ${if (isDnsActive) "✅ Active" else "❌ Inactive"}")
            appendLine("Layer 2 - Chrome Management: ${if (isChromeManaged) "✅ Active" else "❌ Inactive"}")
            appendLine("Layer 3 - Browser Blocking: ${if (areBrowsersBlocked) "✅ Active" else "❌ Inactive"}")
            appendLine("Device Owner: ${if (isDeviceOwnerActive) "✅ Active" else "❌ Inactive"}")
        }
    }
}