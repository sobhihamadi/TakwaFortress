package com.example.takwafortress.repository.interfaces

import com.example.takwafortress.model.entities.IdentifierLicense

interface ILicenseRepository : IRepository<IdentifierLicense> {

    /**
     * Retrieves a license by license key.
     * @param licenseKey - The license key.
     * @returns The license if found.
     * @throws ItemNotFoundException if no license with this key exists.
     */
    suspend fun getByLicenseKey(licenseKey: String): IdentifierLicense

    /**
     * Retrieves a license by device ID (hardware ID).
     * @param deviceId - The device hardware ID.
     * @returns The license if found.
     * @throws ItemNotFoundException if no license bound to this device exists.
     */
    suspend fun getByDeviceId(deviceId: String): IdentifierLicense

    /**
     * Checks if a license key exists and is valid.
     */
    suspend fun isLicenseValid(licenseKey: String): Boolean

    /**
     * Revokes a license (marks it as revoked).
     * @param licenseKey - The license key to revoke.
     * @param reason - The reason for revocation.
     */
    suspend fun revokeLicense(licenseKey: String, reason: String)

    /**
     * Gets the current active license for this device.
     * @returns The active license or null if no license exists.
     */
    suspend fun getCurrentLicense(): IdentifierLicense?

    /**
     * Saves the current license as the active license.
     */
    suspend fun setCurrentLicense(license: IdentifierLicense)
}