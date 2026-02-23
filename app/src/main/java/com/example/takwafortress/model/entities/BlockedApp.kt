package com.example.takwafortress.model.entities

import com.example.takwafortress.model.interfaces.ID
import com.example.takwafortress.model.interfaces.IIdentifiable

/**
 * Enhanced BlockedApp entity supporting:
 * 1. Installed apps (can be blocked immediately)
 * 2. Pre-blocked apps (will be blocked when installed)
 */
open class BlockedApp(
    private val packageName: String,
    private val appName: String,
    private val isSystemApp: Boolean,
    private val isSuspended: Boolean,
    private val blockReason: String,
    private val detectedDate: Long,
    private val isInstalled: Boolean = true,  // ✅ NEW: Track if app is actually installed
    private val isPreBlocked: Boolean = false // ✅ NEW: Was this added before installation?
) {

    fun getPackageName(): String = packageName
    fun getAppName(): String = appName
    fun getIsSystemApp(): Boolean = isSystemApp
    fun getIsSuspended(): Boolean = isSuspended
    fun getBlockReason(): String = blockReason
    fun getDetectedDate(): Long = detectedDate
    fun getIsInstalled(): Boolean = isInstalled
    fun getIsPreBlocked(): Boolean = isPreBlocked

    fun canBeUninstalled(): Boolean = !isSystemApp

    fun isBlacklistedApp(): Boolean {
        return packageName in NUCLEAR_BLACKLIST
    }

    fun shouldBeHidden(): Boolean {
        // Hide if nuclear AND installed
        return isBlacklistedApp() && isInstalled
    }

    fun shouldBeSuspended(): Boolean {
        // Suspend if not blacklisted, installed, and marked as suspended
        return !isBlacklistedApp() && isSuspended && isInstalled
    }

    /**
     * ✅ NEW: Check if this is a "waiting to block" entry
     */
    fun isPendingBlock(): Boolean {
        return isPreBlocked && !isInstalled
    }

    companion object {
        val NUCLEAR_BLACKLIST = setOf(
            "org.telegram.messenger",
            "com.reddit.frontpage",
            "com.twitter.android",
            "com.discord"
        )

        fun isNuclearApp(packageName: String): Boolean {
            return packageName in NUCLEAR_BLACKLIST
        }
    }
}

class IdentifierBlockedApp(
    private val id: ID,
    packageName: String,
    appName: String,
    isSystemApp: Boolean,
    isSuspended: Boolean,
    blockReason: String,
    detectedDate: Long,
    isInstalled: Boolean = true,
    isPreBlocked: Boolean = false
) : BlockedApp(
    packageName,
    appName,
    isSystemApp,
    isSuspended,
    blockReason,
    detectedDate,
    isInstalled,
    isPreBlocked
), IIdentifiable {

    override fun getId(): ID = id
}