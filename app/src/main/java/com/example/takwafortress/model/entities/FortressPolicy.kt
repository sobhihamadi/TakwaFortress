package com.example.takwafortress.model.entities

import com.example.takwafortress.model.enums.ActivationMethod
import com.example.takwafortress.model.enums.CommitmentPlan
import com.example.takwafortress.model.enums.FortressState
import com.example.takwafortress.model.interfaces.ID
import com.example.takwafortress.model.interfaces.IFortress
import com.example.takwafortress.model.interfaces.IIdentifiable


open class FortressPolicy(
    private val commitmentPlan: CommitmentPlan,
    private val activationTimestamp: Long,
    private val expiryTimestamp: Long,
    private val isDeviceOwnerActive: Boolean,
    private val activationMethod: ActivationMethod,
    private val blockedApps: Set<String>,
    private val isDnsForced: Boolean,
    private val isSafeModeDisabled: Boolean,
    private val isFactoryResetBlocked: Boolean,
    private val isTimeProtectionActive: Boolean,
    private val currentState: FortressState
) : IFortress {

    override fun getCommitmentPlan(): CommitmentPlan = commitmentPlan

    fun getActivationTimestamp(): Long = activationTimestamp

    fun getExpiryTimestamp(): Long = expiryTimestamp

    fun getIsDeviceOwnerActive(): Boolean = isDeviceOwnerActive

    fun getActivationMethod(): ActivationMethod = activationMethod

    fun getBlockedApps(): Set<String> = blockedApps

    fun getIsDnsForced(): Boolean = isDnsForced

    fun getIsSafeModeDisabled(): Boolean = isSafeModeDisabled

    fun getIsFactoryResetBlocked(): Boolean = isFactoryResetBlocked

    fun getIsTimeProtectionActive(): Boolean = isTimeProtectionActive

    override fun getFortressState(): FortressState = currentState

    override fun isUnlockEligible(): Boolean {
        val timeExpired = System.currentTimeMillis() >= expiryTimestamp
        return timeExpired && currentState == FortressState.ACTIVE
    }

    override fun getRemainingDays(): Int {
        val remaining = expiryTimestamp - System.currentTimeMillis()
        return if (remaining > 0) {
            (remaining / (1000 * 60 * 60 * 24)).toInt()
        } else {
            0
        }
    }

    override fun getProgressPercentage(): Float {
        val total = expiryTimestamp - activationTimestamp
        val elapsed = System.currentTimeMillis() - activationTimestamp
        val percentage = (elapsed.toFloat() / total.toFloat()) * 100
        return percentage.coerceIn(0f, 100f)
    }

    fun getRemainingHours(): Int {
        val remaining = expiryTimestamp - System.currentTimeMillis()
        return if (remaining > 0) {
            (remaining / (1000 * 60 * 60)).toInt()
        } else {
            0
        }
    }

    fun isExpired(): Boolean {
        return System.currentTimeMillis() >= expiryTimestamp
    }

    fun canStartNewPeriod(): Boolean {
        return currentState.canStartNewPeriod()
    }

    fun isFullyProtected(): Boolean {
        return isDeviceOwnerActive &&
                isDnsForced &&
                isSafeModeDisabled &&
                isFactoryResetBlocked &&
                isTimeProtectionActive
    }

    fun getProtectionScore(): Int {
        var score = 0
        if (isDeviceOwnerActive) score += 20
        if (isDnsForced) score += 20
        if (isSafeModeDisabled) score += 20
        if (isFactoryResetBlocked) score += 20
        if (isTimeProtectionActive) score += 20
        return score
    }
}

class IdentifierFortressPolicy(
    private val id: ID,
    commitmentPlan: CommitmentPlan,
    activationTimestamp: Long,
    expiryTimestamp: Long,
    isDeviceOwnerActive: Boolean,
    activationMethod: ActivationMethod,
    blockedApps: Set<String>,
    isDnsForced: Boolean,
    isSafeModeDisabled: Boolean,
    isFactoryResetBlocked: Boolean,
    isTimeProtectionActive: Boolean,
    currentState: FortressState
) : FortressPolicy(
    commitmentPlan,
    activationTimestamp,
    expiryTimestamp,
    isDeviceOwnerActive,
    activationMethod,
    blockedApps,
    isDnsForced,
    isSafeModeDisabled,
    isFactoryResetBlocked,
    isTimeProtectionActive,
    currentState
), IIdentifiable {

    override fun getId(): ID = id
}