package com.example.takwafortress.util.exceptions


/**
 * Base exception for all validation errors.
 */
sealed class ValidationException(message: String) : Exception(message) {

    /**
     * Required field is missing.
     */
    class RequiredFieldMissing(fieldName: String) : ValidationException("Required field missing: $fieldName")

    /**
     * Invalid email format.
     */
    class InvalidEmail(email: String) : ValidationException("Invalid email: $email")

    /**
     * Invalid license key format.
     */
    class InvalidLicenseKeyFormat(key: String) : ValidationException("Invalid license key format: $key")

    /**
     * Invalid hardware ID.
     */
    class InvalidHardwareId(hwid: String) : ValidationException("Invalid hardware ID: $hwid")

    /**
     * Invalid commitment plan.
     */
    class InvalidCommitmentPlan(plan: String) : ValidationException("Invalid commitment plan: $plan")

    /**
     * Invalid date/time.
     */
    class InvalidDateTime(timestamp: Long) : ValidationException("Invalid date/time: $timestamp")

    /**
     * Invalid package name.
     */
    class InvalidPackageName(packageName: String) : ValidationException("Invalid package name: $packageName")

    /**
     * Validation failed with custom reason.
     */
    class Custom(reason: String) : ValidationException(reason)
}