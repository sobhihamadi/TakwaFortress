package com.example.takwafortress.model.entities

import com.example.takwafortress.model.enums.SubscriptionStatus
import com.example.takwafortress.model.interfaces.ID
import com.example.takwafortress.model.interfaces.IIdentifiable
import com.google.firebase.Timestamp

/**
 * Core User entity representing a user in the system.
 * Follows Single Responsibility Principle - handles only user data.
 */
data class User(
    val email: String,
    val deviceId: String,
    val subscriptionStatus: SubscriptionStatus,
    val selectedPlan: String,
    val hasDeviceOwner: Boolean = false,
    val commitmentDays: Int = 0,
    val commitmentStartDate: Timestamp? = null,
    val commitmentEndDate: Timestamp? = null,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {
    /**
     * Checks if user has an active subscription.
     */
    fun hasActiveSubscription(): Boolean {
        return subscriptionStatus == SubscriptionStatus.ACTIVE ||
                subscriptionStatus == SubscriptionStatus.TRIAL
    }

    /**
     * Checks if user is in an active commitment period.
     */
    fun isInCommitmentPeriod(): Boolean {
        val endDate = commitmentEndDate ?: return false
        return Timestamp.now().seconds < endDate.seconds
    }

    /**
     * Checks if user has completed device owner setup.
     */
    fun hasCompletedDeviceOwnerSetup(): Boolean {
        return hasDeviceOwner
    }

    /**
     * Checks if subscription is expired.
     */
    fun isSubscriptionExpired(): Boolean {
        return subscriptionStatus == SubscriptionStatus.EXPIRED ||
                subscriptionStatus == SubscriptionStatus.CANCELLED
    }

    /**
     * Checks if user is on a free trial.
     */
    fun isOnTrial(): Boolean {
        return subscriptionStatus == SubscriptionStatus.TRIAL
    }

    /**
     * Calculates remaining commitment days.
     */
    fun getRemainingCommitmentDays(): Int {
        val endDate = commitmentEndDate ?: return 0
        val now = Timestamp.now().seconds
        val remaining = (endDate.seconds - now) / (24 * 60 * 60)
        return maxOf(0, remaining.toInt())
    }

    /**
     * Calculates commitment progress percentage.
     */
    fun getCommitmentProgressPercentage(): Float {
        if (commitmentDays == 0) return 0f
        val completed = commitmentDays - getRemainingCommitmentDays()
        return (completed.toFloat() / commitmentDays.toFloat()) * 100f
    }
}

/**
 * User entity with identifier for repository operations.
 * Follows Open/Closed Principle - extends User without modifying it.
 */
data class IdentifierUser(
    private val id: ID,
    val user: User
) : IIdentifiable {

    override fun getId(): ID = id

    // Delegate to User for convenience
    val email: String get() = user.email
    val deviceId: String get() = user.deviceId
    val subscriptionStatus: SubscriptionStatus get() = user.subscriptionStatus
    val selectedPlan: String get() = user.selectedPlan
    val hasDeviceOwner: Boolean get() = user.hasDeviceOwner
    val commitmentDays: Int get() = user.commitmentDays
    val commitmentStartDate: Timestamp? get() = user.commitmentStartDate
    val commitmentEndDate: Timestamp? get() = user.commitmentEndDate
    val createdAt: Timestamp get() = user.createdAt
    val updatedAt: Timestamp get() = user.updatedAt

    // Delegate methods
    fun hasActiveSubscription() = user.hasActiveSubscription()
    fun isInCommitmentPeriod() = user.isInCommitmentPeriod()
    fun hasCompletedDeviceOwnerSetup() = user.hasCompletedDeviceOwnerSetup()
    fun isSubscriptionExpired() = user.isSubscriptionExpired()
    fun isOnTrial() = user.isOnTrial()
    fun getRemainingCommitmentDays() = user.getRemainingCommitmentDays()
    fun getCommitmentProgressPercentage() = user.getCommitmentProgressPercentage()
}