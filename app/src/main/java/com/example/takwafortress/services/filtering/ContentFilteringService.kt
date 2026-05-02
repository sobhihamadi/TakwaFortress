package com.example.takwafortress.services.filtering

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import com.example.takwafortress.receivers.DeviceAdminReceiver
import com.example.takwafortress.services.core.DeviceOwnerService
import com.example.takwafortress.util.constants.DnsServers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Comprehensive Content Filtering Service
 *
 * Implements 4-layer protection:
 * 1. DNS Filtering          (CleanBrowsing)
 * 2. Chrome Managed Config  (SafeSearch, no incognito, no DoH)
 * 3. Browser Blocking       (only Chrome allowed)
 * 4. Keyword Detection      (Accessibility Service monitors Chrome address bar)
 */
class ContentFilteringService(private val context: Context) {

    companion object {
        private const val TAG            = "ContentFiltering"
        const val CHROME_PACKAGE         = "com.android.chrome"
        private const val A11Y_SERVICE   =
            "com.example.takwafortress/com.example.takwafortress.services.filtering.KeywordAccessibilityService"

        private val BLOCKED_BROWSERS = setOf(
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.duckduckgo.mobile.android",
            "org.mozilla.focus",
            "com.vivaldi.browser",
            "com.sec.android.app.sbrowser",
            "com.UCMobile.intl",
            "com.kiwibrowser.browser",
            "com.jamal_nasser.browser",
            "us.spotco.fennec_dos",
            "org.torproject.torbrowser",
            "com.ghostery.android.ghostery",
            "com.ecosia.android",
            "com.cloudmosa.puffinFree",
            "acr.browser.lightning",
            "acr.browser.barebones"
        )
    }

    private val deviceOwnerService  = DeviceOwnerService(context)
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent      = ComponentName(context, DeviceAdminReceiver::class.java)

    // ═══════════════════════════════════════════════════════════════════
    // MASTER ACTIVATION — all 4 layers
    // ═══════════════════════════════════════════════════════════════════

    suspend fun activateFullProtection(): ContentFilterResult {
        if (!deviceOwnerService.isDeviceOwner()) {
            return ContentFilterResult.DeviceOwnerRequired
        }

        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🛡️ Activating full content protection (4 layers)…")
                val results      = mutableListOf<String>()
                var allSucceeded = true

                // ── LAYER 1: DNS ──────────────────────────────────────────────
                Log.d(TAG, "Layer 1: DNS filtering…")
                if (setupDnsFiltering()) {
                    results.add("✅ DNS Filtering: Active (CleanBrowsing)")
                } else {
                    results.add("⚠️ DNS Filtering: Failed")
                    allSucceeded = false
                }

                // ── LAYER 2: Chrome managed config ────────────────────────────
                Log.d(TAG, "Layer 2: Chrome configuration…")
                if (configureManagedChrome()) {
                    results.add("✅ Chrome Management: Active")
                } else {
                    results.add("⚠️ Chrome Management: Failed")
                    allSucceeded = false
                }

                // ── LAYER 3: Block other browsers ─────────────────────────────
                Log.d(TAG, "Layer 3: Blocking alternative browsers…")
                val blockResult = blockOtherBrowsers()
                results.add("✅ Browser Blocking: ${blockResult.blocked} browsers blocked")

                // Disable Chrome's built-in DNS-over-HTTPS
                disableChromeDoH()
                results.add("✅ Chrome DoH: Disabled")

                // ── LAYER 4: Keyword detection (Accessibility Service) ─────────
                Log.d(TAG, "Layer 4: Enabling keyword detection…")
                if (enableKeywordDetectionService()) {
                    results.add("✅ Keyword Detection: Active")
                    // Seed default keywords on first activation
                    BlockedKeywordsManager(context).let { mgr ->
                        Log.d(TAG, "  Keyword list: ${mgr.count()} words loaded")
                    }
                } else {
                    results.add("⚠️ Keyword Detection: Could not auto-enable (user may need to enable in Accessibility Settings)")
                    // Not a hard failure — the other 3 layers are still active
                }

                Log.d(TAG, "🎉 Content protection activation complete!")
                ContentFilterResult.Success(results.joinToString("\n"))

            } catch (e: Exception) {
                Log.e(TAG, "❌ Content protection failed", e)
                ContentFilterResult.Failed("Setup failed: ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LAYER 1: DNS FILTERING
    // ═══════════════════════════════════════════════════════════════════

    private fun setupDnsFiltering(): Boolean {
        return try {
            Log.d(TAG, "Setting Private DNS to CleanBrowsing Adult Filter…")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                devicePolicyManager.setGlobalPrivateDnsModeSpecifiedHost(
                    adminComponent,
                    DnsServers.CLEANBROWSING_ADULT_FILTER
                )
                Log.d(TAG, "✅ Private DNS set via API")
            } else {
                Settings.Global.putString(context.contentResolver, "private_dns_mode", "hostname")
                Settings.Global.putString(
                    context.contentResolver,
                    "private_dns_specifier",
                    DnsServers.CLEANBROWSING_ADULT_FILTER
                )
                Log.d(TAG, "✅ Private DNS set via Settings")
            }

            devicePolicyManager.addUserRestriction(
                adminComponent,
                UserManager.DISALLOW_CONFIG_PRIVATE_DNS
            )
            Log.d(TAG, "✅ DNS settings locked")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ DNS setup failed", e)
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LAYER 2: CHROME MANAGED CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════

    private fun configureManagedChrome(): Boolean {
        return try {
            Log.d(TAG, "Configuring Chrome managed policies…")

            // ── Blocked sites list ────────────────────────────────────────────────
            // Add or remove any domain here. "x.com" and "twitter.com" are the same
            // site. Both must be listed. Subdomains are blocked automatically.
            val blockedSites = arrayOf(
                // Social media
                "twitter.com",
                "x.com",

                "tiktok.com",
                "reddit.com",
                "snapchat.com",
                "tumblr.com",
                "discord.com",
                "pinterest.com",
                "telegram.org",
                "web.telegram.org",

                // Adult (backup — DNS already blocks these, this adds Chrome layer)
                "pornhub.com",
                "xvideos.com",
                "xnxx.com",
                "onlyfans.com",
                "redtube.com",
                "youporn.com",

                // Proxy / VPN bypass sites (prevent circumvention)
                "proxysite.com",
                "hide.me",
                "whoer.net",
                "vpnbook.com",
                "ultrasurf.us",
                "anonymouse.org"
            )

            val policies = Bundle().apply {
                // ── Existing policies (unchanged) ─────────────────────────────────
                putBoolean("IncognitoModeAvailability", false)
                putBoolean("ForceSafeSearch", true)
                putInt("ForceYouTubeRestrict", 2)
                putStringArray("ExtensionInstallBlacklist", arrayOf("*"))
                putBoolean("DeveloperToolsDisabled", true)
                putString("DnsOverHttpsMode", "off")
                putString("HomepageLocation", "https://www.google.com")
                putBoolean("HomepageIsNewTabPage", false)
                putBoolean("PasswordManagerEnabled", false)

                // ── NEW: Block specific sites ─────────────────────────────────────
                putStringArray("URLBlocklist", blockedSites)

                // ── NEW: Prevent searching for bypass methods ─────────────────────
                // This blocks the chrome://flags page (used to disable policies)
                putStringArray(
                    "URLBlocklist",
                    blockedSites + arrayOf("chrome://flags", "chrome://settings/privacy")
                )
            }

            devicePolicyManager.setApplicationRestrictions(adminComponent, CHROME_PACKAGE, policies)
            Log.d(TAG, "✅ Chrome configured with ${policies.size()} policies, ${blockedSites.size} sites blocked")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Chrome configuration failed", e)
            false
        }
    }

    private fun disableChromeDoH() {
        Log.d(TAG, "Chrome DoH disabled via managed configuration")
    }

    // ═══════════════════════════════════════════════════════════════════
    // LAYER 3: BLOCK OTHER BROWSERS
    // ═══════════════════════════════════════════════════════════════════

    data class BlockResult(val blocked: Int, val notInstalled: Int)

    private fun blockOtherBrowsers(): BlockResult {
        var blockedCount     = 0
        var notInstalledCount = 0
        for (browserPackage in BLOCKED_BROWSERS) {
            try {
                context.packageManager.getPackageInfo(browserPackage, 0)
                val hidden = devicePolicyManager.setApplicationHidden(adminComponent, browserPackage, true)
                if (hidden) { blockedCount++; Log.d(TAG, "  ✅ Blocked: $browserPackage") }
                else         Log.w(TAG, "  ⚠️ Failed to block: $browserPackage")
            } catch (e: Exception) {
                notInstalledCount++
            }
        }
        Log.d(TAG, "Browser blocking: $blockedCount blocked, $notInstalledCount not installed")
        return BlockResult(blockedCount, notInstalledCount)
    }

    // ═══════════════════════════════════════════════════════════════════
    // LAYER 4: KEYWORD DETECTION — Accessibility Service
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Enables [KeywordAccessibilityService] programmatically using Device Owner
     * privileges. This does NOT require the user to visit Accessibility Settings.
     *
     * Uses DevicePolicyManager.setPermittedAccessibilityServices() to whitelist
     * our service, then writes to Settings.Secure to enable it.
     *
     * @return true if the service was enabled successfully.
     */
    private fun enableKeywordDetectionService(): Boolean {
        return try {
            if (!deviceOwnerService.isDeviceOwner()) {
                Log.w(TAG, "Cannot enable a11y service — not device owner")
                return false
            }

            // Step 1: Whitelist our accessibility service via Device Policy
            // Passing null means ALL services are permitted (most permissive).
            // Passing an explicit list locks down to only those services.
            // We allow all so existing accessibility tools (TalkBack etc.) still work.
            devicePolicyManager.setPermittedAccessibilityServices(adminComponent, null)
            Log.d(TAG, "  ✅ Accessibility services whitelisted")

            // Step 2: Write the enabled services setting
            // Format: "package/FullyQualifiedClassName"
            val currentEnabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            val newEnabled = if (currentEnabled.contains(A11Y_SERVICE)) {
                currentEnabled   // already in the list
            } else if (currentEnabled.isBlank()) {
                A11Y_SERVICE
            } else {
                "$currentEnabled:$A11Y_SERVICE"
            }

            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                newEnabled
            )
            Log.d(TAG, "  ✅ ENABLED_ACCESSIBILITY_SERVICES updated")

            // Step 3: Turn accessibility on globally (may already be on)
            Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1
            )
            Log.d(TAG, "  ✅ Accessibility globally enabled")

            Log.d(TAG, "✅ KeywordAccessibilityService activated programmatically")
            true

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException enabling a11y service: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to enable a11y service: ${e.message}")
            false
        }
    }

    /**
     * Returns true if [KeywordAccessibilityService] is currently enabled.
     */
    fun isKeywordDetectionActive(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            enabled.contains(A11Y_SERVICE)
        } catch (e: Exception) { false }
    }

    // ═══════════════════════════════════════════════════════════════════
    // VERIFICATION & STATUS
    // ═══════════════════════════════════════════════════════════════════

    fun getProtectionStatus(): ProtectionStatus {
        return ProtectionStatus(
            dnsFilterActive       = isDnsFilterActive(),
            chromeManagedActive   = isChromeManaged(),
            browsersBlocked       = countBlockedBrowsers(),
            keywordDetectionActive = isKeywordDetectionActive(),
            isFullyProtected      = isDnsFilterActive() && isChromeManaged() && isKeywordDetectionActive()
        )
    }

    private fun isDnsFilterActive(): Boolean {
        return try {
            val dnsLocked = devicePolicyManager.getUserRestrictions(adminComponent)
                .getBoolean(UserManager.DISALLOW_CONFIG_PRIVATE_DNS, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val currentDns = Settings.Global.getString(
                    context.contentResolver, "private_dns_specifier"
                )
                dnsLocked && currentDns == DnsServers.CLEANBROWSING_ADULT_FILTER
            } else {
                dnsLocked
            }
        } catch (e: Exception) { false }
    }

    private fun isChromeManaged(): Boolean {
        return try {
            val r = devicePolicyManager.getApplicationRestrictions(adminComponent, CHROME_PACKAGE)
            !r.getBoolean("IncognitoModeAvailability", true) &&
                    r.getBoolean("ForceSafeSearch", false) &&
                    r.getString("DnsOverHttpsMode") == "off"
        } catch (e: Exception) { false }
    }

    private fun countBlockedBrowsers(): Int {
        var count = 0
        for (pkg in BLOCKED_BROWSERS) {
            try { if (devicePolicyManager.isApplicationHidden(adminComponent, pkg)) count++ }
            catch (_: Exception) {}
        }
        return count
    }

    // ═══════════════════════════════════════════════════════════════════
    // DNS TEST
    // ═══════════════════════════════════════════════════════════════════

    suspend fun testDnsFilter(): DnsTestResult {
        return withContext(Dispatchers.IO) {
            try {
                val testDomain = "pornhub.com"
                val startTime  = System.currentTimeMillis()
                try {
                    val address   = java.net.InetAddress.getByName(testDomain)
                    val elapsedMs = System.currentTimeMillis() - startTime
                    Log.w(TAG, "⚠️ DNS filter FAILED — resolved to: $address")
                    DnsTestResult.Failed(
                        "DNS filter is NOT working!\n\nResolved to: ${address.hostAddress}\nTime: ${elapsedMs}ms"
                    )
                } catch (e: java.net.UnknownHostException) {
                    val elapsedMs = System.currentTimeMillis() - startTime
                    Log.d(TAG, "✅ DNS filter WORKING — domain blocked")
                    DnsTestResult.Success("✅ DNS filter is WORKING!\n\nBlocked in ${elapsedMs}ms.")
                }
            } catch (e: Exception) {
                DnsTestResult.Error("Test error: ${e.message}")
            }
        }
    }

    fun getStatusReport(): String {
        val s = getProtectionStatus()
        return buildString {
            appendLine("🛡️ CONTENT FILTERING STATUS")
            appendLine("═══════════════════════════════════════")
            appendLine()
            appendLine("LAYER 1: DNS FILTERING")
            appendLine(if (s.dnsFilterActive) "✅ Active — CleanBrowsing Adult Filter" else "❌ INACTIVE")
            appendLine()
            appendLine("LAYER 2: CHROME MANAGEMENT")
            appendLine(if (s.chromeManagedActive) "✅ Active — All policies enforced" else "❌ INACTIVE")
            appendLine()
            appendLine("LAYER 3: BROWSER BLOCKING")
            appendLine("✅ ${s.browsersBlocked} browsers blocked")
            appendLine()
            appendLine("LAYER 4: KEYWORD DETECTION")
            appendLine(if (s.keywordDetectionActive) "✅ Active — monitoring Chrome search bar" else "❌ INACTIVE")
            appendLine()
            appendLine("═══════════════════════════════════════")
            appendLine(if (s.isFullyProtected) "🎉 FULL PROTECTION ACTIVE" else "⚠️ PROTECTION INCOMPLETE")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// RESULT & STATUS CLASSES
// ═══════════════════════════════════════════════════════════════════

sealed class ContentFilterResult {
    data class Success(val details: String) : ContentFilterResult()
    object DeviceOwnerRequired : ContentFilterResult()
    data class Failed(val reason: String) : ContentFilterResult()
}

data class ProtectionStatus(
    val dnsFilterActive        : Boolean,
    val chromeManagedActive    : Boolean,
    val browsersBlocked        : Int,
    val keywordDetectionActive : Boolean,   // ← NEW field
    val isFullyProtected       : Boolean
)

sealed class DnsTestResult {
    data class Success(val message: String) : DnsTestResult()
    data class Failed(val message: String)  : DnsTestResult()
    data class Error(val error: String)     : DnsTestResult()
}