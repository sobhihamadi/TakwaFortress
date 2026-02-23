package com.example.takwafortress.repository.interfaces

import com.example.takwafortress.model.entities.IdentifierUser
import com.example.takwafortress.model.interfaces.ID

/**
 * Repository interface for User operations.
 *
 * This interface follows the Dependency Inversion Principle (DIP) by
 * defining high-level abstractions that concrete implementations must follow.
 *
 * It also follows the Interface Segregation Principle (ISP) by providing
 * only the methods relevant to user management.
 */
interface IUserRepository : IRepository<IdentifierUser> {

    /**
     * Retrieves a user by email address.
     *
     * @param email The user's email address
     * @return The user if found
     * @throws ItemNotFoundException if no user with this email exists
     */
    suspend fun getByEmail(email: String): IdentifierUser

    /**
     * Retrieves a user by device ID.
     *
     * @param deviceId The device hardware ID (ANDROID_ID)
     * @return The user if found
     * @throws ItemNotFoundException if no user with this device ID exists
     */
    suspend fun getByDeviceId(deviceId: String): IdentifierUser

    /**
     * Checks if a user exists with the given email.
     *
     * @param email The email to check
     * @return true if a user exists with this email, false otherwise
     */
    suspend fun existsByEmail(email: String): Boolean

    /**
     * Checks if a user exists with the given device ID.
     *
     * @param deviceId The device ID to check
     * @return true if a user exists with this device ID, false otherwise
     */
    suspend fun existsByDeviceId(deviceId: String): Boolean

    /**
     * Gets the current active user.
     *
     * In this system, only one user can be active per device (singleton pattern).
     *
     * @return The active user or null if no user is logged in
     */
    suspend fun getCurrentUser(): IdentifierUser?

    /**
     * Sets the current active user.
     *
     * This method saves the user and marks them as the current active user.
     *
     * @param user The user to set as current
     */
    suspend fun setCurrentUser(user: IdentifierUser)

    /**
     * Clears the current user (logout).
     *
     * This removes the reference to the current active user but doesn't
     * delete the user data from storage.
     */
    suspend fun clearCurrentUser()

    // Legacy methods for backward compatibility

    /**
     * Retrieves a user by license key (legacy).
     *
     * @param licenseKey The license key
     * @return The user if found
     * @throws ItemNotFoundException if no user with this license key exists
     */
    @Deprecated("Use Firebase Auth UID instead", ReplaceWith("get(id)"))
    suspend fun getByLicenseKey(licenseKey: String): IdentifierUser

    /**
     * Checks if a user exists with the given license key (legacy).
     *
     * @param licenseKey The license key to check
     * @return true if a user exists with this license key, false otherwise
     */
    @Deprecated("Use Firebase Auth UID instead", ReplaceWith("exists(id)"))
    suspend fun existsByLicenseKey(licenseKey: String): Boolean
}