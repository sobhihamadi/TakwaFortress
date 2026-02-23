package com.example.takwafortress.util.exceptions


/**
 * Base exception for all license-related errors.
 */
sealed class LicenseException(message: String) : Exception(message) {

    /**
     * License key is invalid or not found.
     */
    class InvalidLicenseKey(key: String) : LicenseException("Invalid license key: $key")

    /**
     * License is already bound to another device.
     */
    class AlreadyBound(deviceId: String) : LicenseException("License already bound to device: $deviceId")

    /**
     * License has been revoked (factory reset detected).
     */
    class Revoked(reason: String) : LicenseException("License revoked: $reason")

    /**
     * License has expired (subscription ended).
     */
    class Expired(expiryDate: Long) : LicenseException("License expired on: $expiryDate")

    /**
     * Network error during license validation.
     */
    class NetworkError(cause: Throwable?) : LicenseException("Network error: ${cause?.message}")

    /**
     * License validation failed on server.
     */
    class ValidationFailed(reason: String) : LicenseException("Validation failed: $reason")

    /**
     * No license found on device.
     */
    class NotFound : LicenseException("No license found on this device")
}