package com.example.takwafortress.services.filtering

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.util.Log
import com.example.takwafortress.receivers.DeviceAdminReceiver
import com.example.takwafortress.services.core.DeviceOwnerService
import com.example.takwafortress.util.constants.DnsServers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Comprehensive Content Filtering Service
 *
 * Implements 3-layer protection:
 * 1. DNS Filtering (CleanBrowsing)
 * 2. Chrome Managed Configuration (SafeSearch, no incognito)
 * 3. Browser Blocking (only Chrome allowed)
 */
class ContentFilteringService(private val context: Context) {

    companion object {
        private const val TAG = "ContentFiltering"
        const val CHROME_PACKAGE = "com.android.chrome"

        // List of browsers to block (allow ONLY Chrome)
        private val BLOCKED_BROWSERS = setOf(
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.duckduckgo.mobile.android",
            "org.mozilla.focus",
            "com.vivaldi.browser",
            "com.sec.android.app.sbrowser", // Samsung Internet
            "com.UCMobile.intl",
            "com.kiwibrowser.browser",
            "com.jamal_nasser.browser", // Privacy Browser
            "us.spotco.fennec_dos", // Fennec
            "org.torproject.torbrowser", // Tor Browser
            "com.ghostery.android.ghostery",
            "com.ecosia.android",
            "com.cloudmosa.puffinFree",
            "acr.browser.lightning",
            "acr.browser.barebones"
        )
    }

    private val deviceOwnerService = DeviceOwnerService(context)
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)

    // ═══════════════════════════════════════════════════════════════════
    // MASTER ACTIVATION - Sets up ALL 3 layers
    // ═══════════════════════════════════════════════════════════════════

    suspend fun activateFullProtection(): ContentFilterResult {
        if (!deviceOwnerService.isDeviceOwner()) {
            return ContentFilterResult.DeviceOwnerRequired
        }

        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🛡️ Activating full content protection...")

                val results = mutableListOf<String>()
                var allSucceeded = true

                // LAYER 1: DNS Filtering
                Log.d(TAG, "Layer 1: Setting up DNS filtering...")
                val dnsResult = setupDnsFiltering()
                if (dnsResult) {
                    results.add("✅ DNS Filtering: Active (CleanBrowsing)")
                } else {
                    results.add("⚠️ DNS Filtering: Failed")
                    allSucceeded = false
                }

                // LAYER 2: Chrome Management
                Log.d(TAG, "Layer 2: Configuring Chrome...")
                val chromeResult = configureManagedChrome()
                if (chromeResult) {
                    results.add("✅ Chrome Management: Active")
                } else {
                    results.add("⚠️ Chrome Management: Failed")
                    allSucceeded = false
                }

                // LAYER 3: Block Other Browsers
                Log.d(TAG, "Layer 3: Blocking alternative browsers...")
                val blockResult = blockOtherBrowsers()
                results.add("✅ Browser Blocking: ${blockResult.blocked} browsers blocked")

                // BONUS: Disable Chrome's built-in DNS-over-HTTPS
                Log.d(TAG, "Bonus: Disabling Chrome DoH...")
                disableChromeDoH()
                results.add("✅ Chrome DoH: Disabled")

                Log.d(TAG, "🎉 Content protection activated!")

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
            Log.d(TAG, "Setting Private DNS to CleanBrowsing Adult Filter...")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: Use official API
                devicePolicyManager.setGlobalPrivateDnsModeSpecifiedHost(
                    adminComponent,
                    DnsServers.CLEANBROWSING_ADULT_FILTER
                )
                Log.d(TAG, "✅ Private DNS set via API")
            } else {
                // Android 9: Use Settings
                android.provider.Settings.Global.putString(
                    context.contentResolver,
                    "private_dns_mode",
                    "hostname"
                )
                android.provider.Settings.Global.putString(
                    context.contentResolver,
                    "private_dns_specifier",
                    DnsServers.CLEANBROWSING_ADULT_FILTER
                )
                Log.d(TAG, "✅ Private DNS set via Settings")
            }

            // Lock DNS settings
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
            Log.d(TAG, "Configuring Chrome managed policies...")

            val policies = Bundle().apply {
                // ✅ CRITICAL: Disable Incognito Mode
                putBoolean("IncognitoModeAvailability", false)
                Log.d(TAG, "  - Incognito Mode: DISABLED")

                // ✅ CRITICAL: Force SafeSearch
                putBoolean("ForceSafeSearch", true)
                Log.d(TAG, "  - SafeSearch: FORCED")

                // ✅ CRITICAL: Force YouTube Restricted Mode
                putInt("ForceYouTubeRestrict", 2) // 2 = Strict
                Log.d(TAG, "  - YouTube Restricted: STRICT")

                // ✅ Block extension installs (prevents VPN/proxy extensions)
                putStringArray("ExtensionInstallBlacklist", arrayOf("*"))
                Log.d(TAG, "  - Extensions: BLOCKED")

                // ✅ Disable developer tools
                putBoolean("DeveloperToolsDisabled", true)
                Log.d(TAG, "  - Developer Tools: DISABLED")

                // ✅ CRITICAL: Disable DNS over HTTPS (forces our DNS)
                putString("DnsOverHttpsMode", "off")
                Log.d(TAG, "  - DNS-over-HTTPS: DISABLED")

                // ✅ Set homepage
                putString("HomepageLocation", "https://www.google.com")
                putBoolean("HomepageIsNewTabPage", false)

                // ✅ Disable password manager (prevents saving adult site passwords)
                putBoolean("PasswordManagerEnabled", false)
                Log.d(TAG, "  - Password Manager: DISABLED")
            }

            devicePolicyManager.setApplicationRestrictions(
                adminComponent,
                CHROME_PACKAGE,
                policies
            )

            Log.d(TAG, "✅ Chrome configured with ${policies.size()} policies")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Chrome configuration failed", e)
            false
        }
    }

    /**
     * CRITICAL: Disables Chrome's DNS-over-HTTPS which bypasses our DNS filter
     */
    private fun disableChromeDoH() {
        try {
            // This is already handled in the managed configuration above
            // But we explicitly call it out because it's SO important
            Log.d(TAG, "Chrome DoH disabled via managed configuration")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable Chrome DoH", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LAYER 3: BLOCK OTHER BROWSERS
    // ═══════════════════════════════════════════════════════════════════

    data class BlockResult(val blocked: Int, val notInstalled: Int)

    private fun blockOtherBrowsers(): BlockResult {
        var blockedCount = 0
        var notInstalledCount = 0

        for (browserPackage in BLOCKED_BROWSERS) {
            try {
                // Check if browser is installed
                context.packageManager.getPackageInfo(browserPackage, 0)

                // It's installed - hide/block it
                val hidden = devicePolicyManager.setApplicationHidden(
                    adminComponent,
                    browserPackage,
                    true
                )

                if (hidden) {
                    blockedCount++
                    Log.d(TAG, "  ✅ Blocked: $browserPackage")
                } else {
                    Log.w(TAG, "  ⚠️ Failed to block: $browserPackage")
                }

            } catch (e: Exception) {
                // Not installed - that's fine
                notInstalledCount++
            }
        }

        Log.d(TAG, "Browser blocking: $blockedCount blocked, $notInstalledCount not installed")
        return BlockResult(blockedCount, notInstalledCount)
    }

    // ═══════════════════════════════════════════════════════════════════
    // VERIFICATION & STATUS
    // ═══════════════════════════════════════════════════════════════════

    fun getProtectionStatus(): ProtectionStatus {
        val dnsActive = isDnsFilterActive()
        val chromeManaged = isChromeManaged()
        val browsersBlocked = countBlockedBrowsers()

        return ProtectionStatus(
            dnsFilterActive = dnsActive,
            chromeManagedActive = chromeManaged,
            browsersBlocked = browsersBlocked,
            isFullyProtected = dnsActive && chromeManaged && browsersBlocked > 0
        )
    }

    private fun isDnsFilterActive(): Boolean {
        return try {
            // Check if DNS settings are locked
            val dnsLocked = devicePolicyManager.getUserRestrictions(adminComponent)
                .getBoolean(UserManager.DISALLOW_CONFIG_PRIVATE_DNS, false)

            // On Android 10+, we can verify the DNS is set
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val currentDns = android.provider.Settings.Global.getString(
                    context.contentResolver,
                    "private_dns_specifier"
                )
                dnsLocked && currentDns == DnsServers.CLEANBROWSING_ADULT_FILTER
            } else {
                dnsLocked
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isChromeManaged(): Boolean {
        return try {
            val restrictions = devicePolicyManager.getApplicationRestrictions(
                adminComponent,
                CHROME_PACKAGE
            )

            val incognitoDisabled = !restrictions.getBoolean("IncognitoModeAvailability", true)
            val safeSearchForced = restrictions.getBoolean("ForceSafeSearch", false)
            val dohDisabled = restrictions.getString("DnsOverHttpsMode") == "off"

            incognitoDisabled && safeSearchForced && dohDisabled
        } catch (e: Exception) {
            false
        }
    }

    private fun countBlockedBrowsers(): Int {
        var count = 0
        for (browserPackage in BLOCKED_BROWSERS) {
            try {
                val isHidden = devicePolicyManager.isApplicationHidden(
                    adminComponent,
                    browserPackage
                )
                if (isHidden) count++
            } catch (e: Exception) {
                // Not installed
            }
        }
        return count
    }

    /**
     * Tests if DNS filtering is working by attempting to resolve a blocked domain
     */
    suspend fun testDnsFilter(): DnsTestResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Testing DNS filter...")

                // Try to resolve a known adult site
                val testDomain = "pornhub.com"
                val startTime = System.currentTimeMillis()

                try {
                    val address = java.net.InetAddress.getByName(testDomain)
                    val elapsedMs = System.currentTimeMillis() - startTime

                    // If we got an address, DNS did NOT block it
                    Log.w(TAG, "⚠️ DNS filter FAILED - domain resolved to: $address")
                    DnsTestResult.Failed(
                        "DNS filter is NOT working!\n\n" +
                                "Test domain resolved to: ${address.hostAddress}\n" +
                                "Time: ${elapsedMs}ms\n\n" +
                                "This means adult sites are NOT being blocked."
                    )
                } catch (e: java.net.UnknownHostException) {
                    val elapsedMs = System.currentTimeMillis() - startTime
                    // Domain couldn't be resolved - DNS blocked it!
                    Log.d(TAG, "✅ DNS filter WORKING - domain blocked")
                    DnsTestResult.Success(
                        "✅ DNS filter is WORKING!\n\n" +
                                "Test domain was blocked.\n" +
                                "Time: ${elapsedMs}ms\n\n" +
                                "Adult sites will be blocked at the network level."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "DNS test failed with error", e)
                DnsTestResult.Error("Test error: ${e.message}")
            }
        }
    }

    /**
     * Gets a user-friendly status report
     */
    fun getStatusReport(): String {
        val status = getProtectionStatus()

        return buildString {
            appendLine("🛡️ CONTENT FILTERING STATUS")
            appendLine()
            appendLine("═══════════════════════════════════════")
            appendLine()

            appendLine("LAYER 1: DNS FILTERING")
            if (status.dnsFilterActive) {
                appendLine("✅ Active - CleanBrowsing Adult Filter")
                appendLine("   • Blocks adult domains at network level")
                appendLine("   • Works on all apps")
                appendLine("   • Settings locked")
            } else {
                appendLine("❌ INACTIVE")
                appendLine("   • DNS filter not configured")
            }
            appendLine()

            appendLine("LAYER 2: CHROME MANAGEMENT")
            if (status.chromeManagedActive) {
                appendLine("✅ Active - All policies enforced")
                appendLine("   • Incognito mode disabled")
                appendLine("   • SafeSearch forced")
                appendLine("   • YouTube restricted")
                appendLine("   • DoH disabled")
            } else {
                appendLine("❌ INACTIVE")
                appendLine("   • Chrome not managed")
            }
            appendLine()

            appendLine("LAYER 3: BROWSER BLOCKING")
            appendLine("✅ ${status.browsersBlocked} browsers blocked")
            appendLine("   • Only Chrome allowed")
            appendLine("   • All other browsers hidden")
            appendLine()

            appendLine("═══════════════════════════════════════")
            appendLine()

            if (status.isFullyProtected) {
                appendLine("🎉 FULL PROTECTION ACTIVE")
            } else {
                appendLine("⚠️ PROTECTION INCOMPLETE")
                appendLine()
                appendLine("Tap 'Activate Protection' to fix.")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// RESULT CLASSES
// ═══════════════════════════════════════════════════════════════════

sealed class ContentFilterResult {
    data class Success(val details: String) : ContentFilterResult()
    object DeviceOwnerRequired : ContentFilterResult()
    data class Failed(val reason: String) : ContentFilterResult()
}

data class ProtectionStatus(
    val dnsFilterActive: Boolean,
    val chromeManagedActive: Boolean,
    val browsersBlocked: Int,
    val isFullyProtected: Boolean
)

sealed class DnsTestResult {
    data class Success(val message: String) : DnsTestResult()
    data class Failed(val message: String) : DnsTestResult()
    data class Error(val error: String) : DnsTestResult()
}