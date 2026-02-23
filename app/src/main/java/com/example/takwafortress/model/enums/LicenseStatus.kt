package com.example.takwafortress.model.enums

enum class LicenseStatus {
    NONE,           // Never purchased or revoked
    PENDING,        // Payment processing (Whop webhook pending)
    ACTIVE,         // Valid license, can activate fortress
    LOCKED,         // Currently in fortress period
    EXPIRED,        // Period ended, can start new period
    REVOKED;        // License revoked (factory reset detected)

    fun canActivateFortress(): Boolean {
        return this == ACTIVE || this == EXPIRED
    }

    fun isValid(): Boolean {
        return this == ACTIVE || this == LOCKED || this == EXPIRED
    }

    fun requiresPayment(): Boolean {
        return this == NONE || this == REVOKED
    }
}