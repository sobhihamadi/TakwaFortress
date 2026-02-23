package com.example.takwafortress.util.exceptions


/**
 * Base exception for all Device Owner related errors.
 */
sealed class DeviceOwnerException(message: String) : Exception(message) {

    /**
     * Device Owner is not active.
     */
    class NotActivated : DeviceOwnerException("Device Owner is not activated")

    /**
     * Device Owner activation failed.
     */
    class ActivationFailed(reason: String) : DeviceOwnerException("Activation failed: $reason")

    /**
     * Accounts exist on device (prevents Device Owner activation).
     */
    class AccountsExist : DeviceOwnerException("Google or work accounts must be removed before activation")

    /**
     * Knox activation failed (Samsung devices).
     */
    class KnoxActivationFailed(reason: String) : DeviceOwnerException("Knox activation failed: $reason")

    /**
     * Wireless ADB pairing failed.
     */
    class WirelessAdbFailed(reason: String) : DeviceOwnerException("Wireless ADB failed: $reason")

    /**
     * Device Owner restriction failed to apply.
     */
    class RestrictionFailed(restriction: String) : DeviceOwnerException("Failed to apply restriction: $restriction")

    /**
     * App hiding/suspension failed.
     */
    class AppManagementFailed(packageName: String) : DeviceOwnerException("Failed to manage app: $packageName")

    /**
     * Another app is already Device Owner.
     */
    class AnotherOwnerExists(ownerPackage: String) : DeviceOwnerException("Another Device Owner exists: $ownerPackage")
}