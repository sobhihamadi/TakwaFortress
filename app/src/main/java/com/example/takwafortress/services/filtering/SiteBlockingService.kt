package com.example.takwafortress.services.filtering

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.example.takwafortress.receivers.DeviceAdminReceiver
import com.example.takwafortress.repository.implementations.LocalBlockedSiteRepository
import com.example.takwafortress.services.core.DeviceOwnerService

/**
 * Manages user-defined site blocking via Chrome's URLBlocklist managed policy.
 *
 * Flow:
 *   User types URL → normalizeDomain() → LocalBlockedSiteRepository.add()
 *                 → applyToChrome() merges user list + hardcoded list
 *                 → DevicePolicyManager.setApplicationRestrictions(Chrome)
 */
class SiteBlockingService(private val context: Context) {

    companion object {
        private const val TAG = "SiteBlockingService"
        const val CHROME_PACKAGE = "com.android.chrome"

        /** Hardcoded sites that are always blocked (matches ContentFilteringService list). */
        private val HARDCODED_BLOCKED = arrayOf(
            "twitter.com", "x.com", "tiktok.com", "reddit.com", "snapchat.com",
            "tumblr.com", "discord.com", "pinterest.com", "telegram.org",
            "web.telegram.org", "pornhub.com", "xvideos.com", "xnxx.com",
            "onlyfans.com", "redtube.com", "youporn.com",
            "proxysite.com", "hide.me", "whoer.net", "vpnbook.com",
            "ultrasurf.us", "anonymouse.org", "chrome://flags",
            "chrome://settings/privacy"
        )
    }

    private val repository = LocalBlockedSiteRepository(context)
    private val deviceOwnerService = DeviceOwnerService(context)
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, DeviceAdminReceiver::class.java)

    /** Normalizes any URL/domain input to a bare hostname. */
    fun normalizeDomain(input: String): String {
        return input
            .trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore("/")   // drop any path
            .substringBefore("?")   // drop query string
            .ifEmpty { input.trim() }
    }

    /** Adds a site and immediately re-applies Chrome policy. Returns false if not Device Owner. */
    suspend fun blockSite(rawInput: String): SiteBlockResult {
        val domain = normalizeDomain(rawInput)
        if (domain.isBlank() || !domain.contains(".")) {
            return SiteBlockResult.InvalidUrl
        }
        if (!deviceOwnerService.isDeviceOwner()) {
            return SiteBlockResult.DeviceOwnerRequired
        }
        repository.add(domain)
        return when (applyToChrome()) {
            true  -> SiteBlockResult.Success(domain)
            false -> SiteBlockResult.ApplyFailed
        }
    }

    /** Removes a site by id and re-applies Chrome policy. */
    suspend fun unblockSite(id: String) {
        repository.remove(id)
        applyToChrome()
    }

    suspend fun getAllBlockedSites() = repository.getAll()

    /** Clears user sites (called by FortressClearService). */
    suspend fun clearAllUserSites() {
        repository.clear()
        applyToChrome()
    }

    /**
     * Builds the merged URLBlocklist (hardcoded + user-defined) and pushes it
     * to Chrome via DevicePolicyManager. Returns true on success.
     */
    suspend fun applyToChrome(): Boolean {
        if (!deviceOwnerService.isDeviceOwner()) return false
        return try {
            val userDomains = repository.getAllDomains()
            val merged = (HARDCODED_BLOCKED.toList() + userDomains).distinct().toTypedArray()

            val current = try {
                dpm.getApplicationRestrictions(admin, CHROME_PACKAGE)
            } catch (e: Exception) { Bundle() }

            // Preserve all existing Chrome policies, only overwrite URLBlocklist
            val updated = Bundle(current).apply {
                putStringArray("URLBlocklist", merged)
            }

            dpm.setApplicationRestrictions(admin, CHROME_PACKAGE, updated)
            Log.i(TAG, "✅ Chrome URLBlocklist updated: ${merged.size} entries")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to apply Chrome policy: ${e.message}")
            false
        }
    }
}

sealed class SiteBlockResult {
    data class Success(val domain: String) : SiteBlockResult()
    object InvalidUrl          : SiteBlockResult()
    object DeviceOwnerRequired : SiteBlockResult()
    object ApplyFailed         : SiteBlockResult()
}