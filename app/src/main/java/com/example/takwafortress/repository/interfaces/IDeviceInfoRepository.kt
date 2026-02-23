package com.example.takwafortress.repository.interfaces

import com.example.takwafortress.model.entities.IdentifierDeviceInfo


interface IDeviceInfoRepository : IRepository<IdentifierDeviceInfo> {

    /**
     * Gets the current device information.
     * @returns The device info or null if not set.
     */
    suspend fun getCurrentDeviceInfo(): IdentifierDeviceInfo?

    /**
     * Sets the current device information (singleton - only one device info).
     */
    suspend fun setCurrentDeviceInfo(deviceInfo: IdentifierDeviceInfo)

    /**
     * Retrieves device info by hardware ID.
     * @param hardwareId - The hardware ID.
     * @returns The device info if found.
     */
    suspend fun getByHardwareId(hardwareId: String): IdentifierDeviceInfo

    /**
     * Checks if device info exists for this hardware ID.
     */
    suspend fun existsByHardwareId(hardwareId: String): Boolean

    /**
     * Updates the first install date (used for detecting factory reset).
     */
    suspend fun updateFirstInstallDate(hardwareId: String, firstInstallDate: Long)
}