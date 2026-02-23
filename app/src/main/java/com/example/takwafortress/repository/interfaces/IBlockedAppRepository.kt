package com.example.takwafortress.repository.interfaces

import com.example.takwafortress.model.entities.IdentifierBlockedApp

interface IBlockedAppRepository : IRepository<IdentifierBlockedApp> {

    /**
     * Retrieves a blocked app by package name.
     * @param packageName - The app's package name.
     * @returns The blocked app if found.
     * @throws ItemNotFoundException if no blocked app with this package name exists.
     */
    suspend fun getByPackageName(packageName: String): IdentifierBlockedApp

    /**
     * Checks if an app is blocked.
     */
    suspend fun isAppBlocked(packageName: String): Boolean

    /**
     * Gets all currently blocked apps.
     */
    suspend fun getAllBlockedApps(): List<IdentifierBlockedApp>

    /**
     * Gets all suspended apps (grey out but not hidden).
     */
    suspend fun getSuspendedApps(): List<IdentifierBlockedApp>

    /**
     * Gets all nuclear blacklist apps (completely hidden).
     */
    suspend fun getNuclearApps(): List<IdentifierBlockedApp>

    /**
     * Blocks a new app.
     * @param app - The app to block.
     */
    suspend fun blockApp(app: IdentifierBlockedApp)

    /**
     * Unblocks an app (removes from blocked list).
     * @param packageName - The package name to unblock.
     */
    suspend fun unblockApp(packageName: String)

    /**
     * Updates the suspension status of an app.
     */
    suspend fun updateSuspensionStatus(packageName: String, isSuspended: Boolean)
}