package com.example.takwafortress.model.builders

import com.example.takwafortress.model.entities.FortressPolicy
import com.example.takwafortress.model.entities.IdentifierFortressPolicy
import com.example.takwafortress.model.enums.ActivationMethod
import com.example.takwafortress.model.enums.CommitmentPlan
import com.example.takwafortress.model.enums.FortressState
import com.example.takwafortress.model.interfaces.ID

class FortressPolicyBuilder private constructor() {

    private var commitmentPlan: CommitmentPlan? = null
    private var activationTimestamp: Long? = null
    private var expiryTimestamp: Long? = null
    private var isDeviceOwnerActive: Boolean? = null
    private var activationMethod: ActivationMethod? = null
    private var blockedApps: Set<String>? = null
    private var isDnsForced: Boolean? = null
    private var isSafeModeDisabled: Boolean? = null
    private var isFactoryResetBlocked: Boolean? = null
    private var isTimeProtectionActive: Boolean? = null
    private var currentState: FortressState? = null

    fun setCommitmentPlan(commitmentPlan: CommitmentPlan): FortressPolicyBuilder {
        this.commitmentPlan = commitmentPlan
        return this
    }

    fun setActivationTimestamp(activationTimestamp: Long): FortressPolicyBuilder {
        this.activationTimestamp = activationTimestamp
        this.expiryTimestamp = activationTimestamp + (commitmentPlan?.getDurationMillis() ?: 0)
        return this
    }

    fun setExpiryTimestamp(expiryTimestamp: Long): FortressPolicyBuilder {
        this.expiryTimestamp = expiryTimestamp
        return this
    }

    fun setIsDeviceOwnerActive(isDeviceOwnerActive: Boolean): FortressPolicyBuilder {
        this.isDeviceOwnerActive = isDeviceOwnerActive
        return this
    }

    fun setActivationMethod(activationMethod: ActivationMethod): FortressPolicyBuilder {
        this.activationMethod = activationMethod
        return this
    }

    fun setBlockedApps(blockedApps: Set<String>): FortressPolicyBuilder {
        this.blockedApps = blockedApps
        return this
    }

    fun setIsDnsForced(isDnsForced: Boolean): FortressPolicyBuilder {
        this.isDnsForced = isDnsForced
        return this
    }

    fun setIsSafeModeDisabled(isSafeModeDisabled: Boolean): FortressPolicyBuilder {
        this.isSafeModeDisabled = isSafeModeDisabled
        return this
    }

    fun setIsFactoryResetBlocked(isFactoryResetBlocked: Boolean): FortressPolicyBuilder {
        this.isFactoryResetBlocked = isFactoryResetBlocked
        return this
    }

    fun setIsTimeProtectionActive(isTimeProtectionActive: Boolean): FortressPolicyBuilder {
        this.isTimeProtectionActive = isTimeProtectionActive
        return this
    }

    fun setCurrentState(currentState: FortressState): FortressPolicyBuilder {
        this.currentState = currentState
        return this
    }

    fun build(): FortressPolicy {
        require(commitmentPlan != null) { "Commitment plan is required" }
        require(activationTimestamp != null) { "Activation timestamp is required" }
        require(expiryTimestamp != null) { "Expiry timestamp is required" }
        require(isDeviceOwnerActive != null) { "Device owner status is required" }
        require(activationMethod != null) { "Activation method is required" }
        require(blockedApps != null) { "Blocked apps list is required" }
        require(isDnsForced != null) { "DNS forced status is required" }
        require(isSafeModeDisabled != null) { "Safe mode disabled status is required" }
        require(isFactoryResetBlocked != null) { "Factory reset blocked status is required" }
        require(isTimeProtectionActive != null) { "Time protection status is required" }
        require(currentState != null) { "Current state is required" }

        return FortressPolicy(
            commitmentPlan = commitmentPlan!!,
            activationTimestamp = activationTimestamp!!,
            expiryTimestamp = expiryTimestamp!!,
            isDeviceOwnerActive = isDeviceOwnerActive!!,
            activationMethod = activationMethod!!,
            blockedApps = blockedApps!!,
            isDnsForced = isDnsForced!!,
            isSafeModeDisabled = isSafeModeDisabled!!,
            isFactoryResetBlocked = isFactoryResetBlocked!!,
            isTimeProtectionActive = isTimeProtectionActive!!,
            currentState = currentState!!
        )
    }

    companion object {
        fun newBuilder(): FortressPolicyBuilder {
            return FortressPolicyBuilder()
        }
    }
}

class IdentifierFortressPolicyBuilder private constructor() {

    private var id: ID? = null
    private var fortressPolicy: FortressPolicy? = null

    fun setId(id: ID): IdentifierFortressPolicyBuilder {
        require(id.isNotEmpty()) { "ID cannot be empty" }
        this.id = id
        return this
    }

    fun setFortressPolicy(fortressPolicy: FortressPolicy): IdentifierFortressPolicyBuilder {
        this.fortressPolicy = fortressPolicy
        return this
    }

    fun build(): IdentifierFortressPolicy {
        require(id != null) { "ID is required to build IdentifierFortressPolicy" }
        require(fortressPolicy != null) { "FortressPolicy is required to build IdentifierFortressPolicy" }

        val fp = fortressPolicy!!

        return IdentifierFortressPolicy(
            id = id!!,
            commitmentPlan = fp.getCommitmentPlan(),
            activationTimestamp = fp.getActivationTimestamp(),
            expiryTimestamp = fp.getExpiryTimestamp(),
            isDeviceOwnerActive = fp.getIsDeviceOwnerActive(),
            activationMethod = fp.getActivationMethod(),
            blockedApps = fp.getBlockedApps(),
            isDnsForced = fp.getIsDnsForced(),
            isSafeModeDisabled = fp.getIsSafeModeDisabled(),
            isFactoryResetBlocked = fp.getIsFactoryResetBlocked(),
            isTimeProtectionActive = fp.getIsTimeProtectionActive(),
            currentState = fp.getFortressState()
        )
    }

    companion object {
        fun newBuilder(): IdentifierFortressPolicyBuilder {
            return IdentifierFortressPolicyBuilder()
        }
    }
}