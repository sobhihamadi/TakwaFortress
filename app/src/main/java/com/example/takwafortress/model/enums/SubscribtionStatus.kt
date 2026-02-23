package com.example.takwafortress.model.enums

/**
 * Subscription status enum.
 * Replaces LicenseStatus to align with new subscription-based model.
 */
enum class SubscriptionStatus {
    PENDING,    // Registration complete, payment pending
    TRIAL,      // On free trial
    ACTIVE,     // Active paid subscription
    EXPIRED,    // Subscription expired
    CANCELLED;  // Subscription cancelled by user

    /**
     * Checks if this status allows app usage.
     */
    fun isValid(): Boolean {
        return this == ACTIVE || this == TRIAL
    }

    /**
     * Checks if this status allows fortress activation.
     */
    fun canActivateFortress(): Boolean {
        return this == ACTIVE || this == TRIAL
    }

    /**
     * Checks if this status requires payment.
     */
    fun requiresPayment(): Boolean {
        return this == PENDING || this == EXPIRED || this == CANCELLED
    }

    /**
     * Gets user-friendly display name.
     */
    fun getDisplayName(): String {
        return when (this) {
            PENDING -> "Payment Pending"
            TRIAL -> "Free Trial"
            ACTIVE -> "Active"
            EXPIRED -> "Expired"
            CANCELLED -> "Cancelled"
        }
    }
}