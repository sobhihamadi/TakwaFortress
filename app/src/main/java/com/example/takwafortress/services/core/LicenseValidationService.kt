package com.example.takwafortress.services.core

import android.content.Context
import com.example.takwafortress.model.builders.IdentifierLicenseBuilder
import com.example.takwafortress.model.builders.IdentifierUserBuilder
import com.example.takwafortress.model.builders.LicenseBuilder
import com.example.takwafortress.model.builders.UserBuilder
import com.example.takwafortress.model.entities.IdentifierLicense
import com.example.takwafortress.model.entities.IdentifierUser
import com.example.takwafortress.model.enums.LicenseStatus
import com.example.takwafortress.repository.implementations.LocalLicenseRepository
import com.example.takwafortress.repository.implementations.FirebaseUserRepository
import com.example.takwafortress.util.crypto.HardwareIdGenerator
import java.util.UUID

class LicenseValidationService(private val context: Context) {

    private val userRepository = FirebaseUserRepository(context)
    private val licenseRepository = LocalLicenseRepository(context)

    /**
     * Validates a license key and binds it to this device.
     * This is called on first app launch when user enters their license key.
     */
    suspend fun validateAndActivateLicense(
        email: String,
        licenseKey: String
    ): LicenseValidationResult {

        // Step 1: Get hardware ID for this device
        val hardwareId = HardwareIdGenerator.extractHardwareId(context)

        // Step 2: Check if license already exists locally
        val existingLicense = try {
            licenseRepository.getByLicenseKey(licenseKey)
        } catch (e: Exception) {
            null
        }

        if (existingLicense != null) {
            // License exists - check if it's bound to this device
            if (existingLicense.isBoundToDevice(hardwareId)) {
                return LicenseValidationResult.Success(existingLicense)
            } else {
                return LicenseValidationResult.AlreadyBoundToAnotherDevice
            }
        }

        // Step 3: TODO - Call Firebase to validate license key
        // For now, we'll simulate validation (you'll implement Firebase later)
        val isValidFromServer = simulateServerValidation(licenseKey)

        if (!isValidFromServer) {
            return LicenseValidationResult.InvalidLicenseKey
        }

        // Step 4: Create new license bound to this device
        val license = LicenseBuilder.newBuilder()
            .setLicenseKey(licenseKey)
            .setUserId(UUID.randomUUID().toString())
            .setDeviceId(hardwareId)
            .setIsActive(true)
            .setActivationDate(System.currentTimeMillis())
            .setIsRevoked(false)
            .setRevokeReason(null)
            .build()

        val identifierLicense = IdentifierLicenseBuilder.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setLicense(license)
            .build()

        // Step 5: Create user
        val user = UserBuilder.newBuilder()
            .setEmail(email)
            .setLicenseKey(licenseKey)
            .setLicenseStatus(LicenseStatus.ACTIVE)
            .build()

        val identifierUser = IdentifierUserBuilder.newBuilder()
            .setId(license.getUserId())
            .setUser(user)
            .build()

        // Step 6: Save to repositories
        licenseRepository.setCurrentLicense(identifierLicense)
        userRepository.setCurrentUser(identifierUser)

        return LicenseValidationResult.Success(identifierLicense)
    }

    /**
     * Checks if current device has a valid license.
     */
    suspend fun hasValidLicense(): Boolean {
        val currentLicense = licenseRepository.getCurrentLicense()
        return currentLicense?.isValid() == true
    }

    /**
     * Gets the current user on this device.
     */
    suspend fun getCurrentUser(): IdentifierUser? {
        return userRepository.getCurrentUser()
    }

    /**
     * Revokes the license (called when factory reset is detected).
     */
    suspend fun revokeLicense(reason: String) {
        val currentLicense = licenseRepository.getCurrentLicense()
        currentLicense?.let {
            licenseRepository.revokeLicense(it.getLicenseKey(), reason)
        }

        val currentUser = userRepository.getCurrentUser()
        currentUser?.let {
            val updatedUser = IdentifierUserBuilder.newBuilder()
                .setId(it.getId())
                .setUser(
                    UserBuilder.newBuilder()
                        .setEmail(it.email)
                        .setLicenseStatus(LicenseStatus.REVOKED)
                        .build()
                )
                .build()

            userRepository.update(updatedUser)
        }
    }

    /**
     * Simulates server validation (replace with actual Firebase call later).
     */
    private fun simulateServerValidation(licenseKey: String): Boolean {
        // TODO: Replace with actual Firebase API call
        // For now, accept any key that starts with "TAQWA-"
        return licenseKey.startsWith("TAQWA-")
    }
}

sealed class LicenseValidationResult {
    data class Success(val license: IdentifierLicense) : LicenseValidationResult()
    object InvalidLicenseKey : LicenseValidationResult()
    object AlreadyBoundToAnotherDevice : LicenseValidationResult()
    object NetworkError : LicenseValidationResult()
}