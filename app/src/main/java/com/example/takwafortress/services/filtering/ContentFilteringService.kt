package com.example.takwafortress.services.filtering

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
 * 3. Browser Blocking       (dynamic — ALL browsers except Chrome are blocked)
 * 4. Keyword Detection      (Accessibility Service monitors Chrome address bar)
 */
class ContentFilteringService(private val context: Context) {

    companion object {
        private const val TAG            = "ContentFiltering"
        const val CHROME_PACKAGE         = "com.android.chrome"
        private const val A11Y_SERVICE   =
            "com.example.takwafortress/com.example.takwafortress.services.filtering.KeywordAccessibilityService"

        // Fallback hardcoded list — used as a safety net.
        // The primary block mechanism now queries the PackageManager dynamically.
        private val KNOWN_BROWSERS = setOf(
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.brave.browser",
            "com.microsoft.emmx",          // Edge
            "com.duckduckgo.mobile.android",
            "org.mozilla.focus",
            "com.vivaldi.browser",
            "com.sec.android.app.sbrowser",// Samsung Internet
            "com.UCMobile.intl",
            "com.kiwibrowser.browser",
            "com.jamal_nasser.browser",
            "us.spotco.fennec_dos",
            "org.torproject.torbrowser",
            "com.ghostery.android.ghostery",
            "com.ecosia.android",
            "com.cloudmosa.puffinFree",
            "acr.browser.lightning",
            "acr.browser.barebones",
            "com.google.android.apps.chrome",  // alternate Chrome package on some ROMs
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox_beta",
            "com.microsoft.bing",
            "com.yahoo.mobile.client.android.search",
            "mobi.mgeek.tunnybrowser",
            "com.uc.browser.en",
            "com.UCMobile",
            "com.tencent.mtt",
            "mark.via.gp",
            "mark.via",
            "com.helioslauncher.browser",
            "com.mx.browser",
            "com.mx.browser.tablet",
            "com.mycompany.app.soulbrowser",
            "com.fiveheads.browser",
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
                // Sync URLBlocklist with user-blocked sites
                val siteService = SiteBlockingService(context)
                siteService.applyToChrome()

                // ── LAYER 3: Block ALL other browsers (dynamic detection) ──────
                Log.d(TAG, "Layer 3: Blocking ALL alternative browsers dynamically…")
                val blockResult = blockAllNonChromeBrowsers()
                results.add("✅ Browser Blocking: ${blockResult.blocked} browsers blocked")

                disableChromeDoH()
                results.add("✅ Chrome DoH: Disabled")

                // ── LAYER 4: Keyword detection ────────────────────────────────
                Log.d(TAG, "Layer 4: Enabling keyword detection…")
                if (enableKeywordDetectionService()) {
                    results.add("✅ Keyword Detection: Active")
                    BlockedKeywordsManager(context).let { mgr ->
                        Log.d(TAG, "  Keyword list: ${mgr.count()} words loaded")
                    }
                } else {
                    results.add("⚠️ Keyword Detection: Could not auto-enable")
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

            val policies = Bundle().apply {
                putBoolean("IncognitoModeAvailability", false)
                putBoolean("ForceSafeSearch", true)
                putInt("ForceYouTubeRestrict", 2)
                putStringArray("ExtensionInstallBlacklist", arrayOf("*"))
                putBoolean("DeveloperToolsDisabled", true)
                putString("DnsOverHttpsMode", "off")
                putString("HomepageLocation", "https://www.google.com")
                putBoolean("HomepageIsNewTabPage", false)
                putBoolean("PasswordManagerEnabled", false)
            }

            devicePolicyManager.setApplicationRestrictions(adminComponent, CHROME_PACKAGE, policies)
            Log.d(TAG, "✅ Chrome configured with ${policies.size()} policies")
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
    // LAYER 3: DYNAMIC BROWSER BLOCKING
    //
    // This is the KEY fix. Instead of a static list, we:
    //   1. Query PackageManager for every app that can handle http:// URLs
    //   2. Skip Chrome and our own app
    //   3. Hide every other browser via setApplicationHidden
    //   4. Also cover the known-browser list as a safety net
    // ═══════════════════════════════════════════════════════════════════

    data class BlockResult(val blocked: Int, val notInstalled: Int)

    /**
     * Dynamically finds and hides ALL browsers currently installed on the device,
     * except Chrome. Works for any browser — known or unknown.
     */
    fun blockAllNonChromeBrowsers(): BlockResult {
        var blockedCount = 0

        // ── Step 1: Dynamic detection via intent resolution ───────────────────
        // Ask the system "which apps can open a webpage?" — this catches every
        // browser regardless of package name.
        try {
            val httpIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"))
            val httpsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))

            val httpBrowsers = context.packageManager
                .queryIntentActivities(httpIntent, PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }

            val httpsBrowsers = context.packageManager
                .queryIntentActivities(httpsIntent, PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }

            val allDynamicBrowsers = (httpBrowsers + httpsBrowsers).toSet()

            Log.d(TAG, "🔍 Dynamic browser scan found ${allDynamicBrowsers.size} candidates: $allDynamicBrowsers")

            for (pkg in allDynamicBrowsers) {
                if (pkg == CHROME_PACKAGE) continue
                if (pkg == context.packageName) continue

                val blocked = hideSingleBrowser(pkg)
                if (blocked) {
                    blockedCount++
                    Log.d(TAG, "  ✅ Dynamically blocked: $pkg")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Dynamic browser scan failed: ${e.message}")
        }

        // ── Step 2: Known-list sweep (catches browsers with no default handler set) ─
        for (pkg in KNOWN_BROWSERS) {
            if (pkg == CHROME_PACKAGE) continue
            try {
                // Only attempt if installed
                context.packageManager.getPackageInfo(pkg, 0)
                val blocked = hideSingleBrowser(pkg)
                if (blocked) {
                    blockedCount++
                    Log.d(TAG, "  ✅ Known-list blocked: $pkg")
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // Not installed — skip silently
            } catch (e: Exception) {
                Log.w(TAG, "  ⚠️ Could not block $pkg: ${e.message}")
            }
        }

        Log.d(TAG, "Browser blocking complete: $blockedCount blocked")
        return BlockResult(blockedCount, 0)
    }

    /**
     * Hides a single browser package. Returns true if successfully hidden.
     * Falls back to setPackagesSuspended if setApplicationHidden fails.
     */
    private fun hideSingleBrowser(packageName: String): Boolean {
        return try {
            // Primary: completely hide (removes from launcher)
            val hidden = devicePolicyManager.setApplicationHidden(adminComponent, packageName, true)
            if (hidden) return true

            // Fallback: suspend (greys out icon)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val failed = devicePolicyManager.setPackagesSuspended(
                    adminComponent,
                    arrayOf(packageName),
                    true
                )
                failed.isEmpty() // empty array = all succeeded
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "hideSingleBrowser($packageName) failed: ${e.message}")
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // RE-SCAN: called by FortressMonitorService periodically
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Re-scans for any browsers that escaped blocking (e.g. installed after fortress
     * activation). Called by FortressMonitorService every few minutes.
     */
    fun reBlockAnyEscapedBrowsers() {
        if (!deviceOwnerService.isDeviceOwner()) return
        Log.d(TAG, "🔄 Re-scanning for escaped browsers…")
        val result = blockAllNonChromeBrowsers()
        Log.d(TAG, "Re-scan complete: ${result.blocked} browser(s) (re-)blocked")
    }

    // ═══════════════════════════════════════════════════════════════════
    // LAYER 4: KEYWORD DETECTION — Accessibility Service
    // ═══════════════════════════════════════════════════════════════════

    private fun enableKeywordDetectionService(): Boolean {
        return try {
            if (!deviceOwnerService.isDeviceOwner()) {
                Log.w(TAG, "Cannot enable a11y service — not device owner")
                return false
            }

            devicePolicyManager.setPermittedAccessibilityServices(adminComponent, null)
            Log.d(TAG, "  ✅ Accessibility services whitelisted")

            val currentEnabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            val newEnabled = if (currentEnabled.contains(A11Y_SERVICE)) {
                currentEnabled
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
            dnsFilterActive        = isDnsFilterActive(),
            chromeManagedActive    = isChromeManaged(),
            browsersBlocked        = countBlockedBrowsers(),
            keywordDetectionActive = isKeywordDetectionActive(),
            isFullyProtected       = isDnsFilterActive() && isChromeManaged() && isKeywordDetectionActive()
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

    /**
     * Counts how many browsers are currently hidden by Device Owner.
     * Uses dynamic detection so it counts browsers not in the hardcoded list.
     */
    private fun countBlockedBrowsers(): Int {
        var count = 0
        try {
            val httpIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"))
            val browsers = context.packageManager
                .queryIntentActivities(httpIntent, PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }
                .filter { it != CHROME_PACKAGE && it != context.packageName }
                .toSet()

            for (pkg in browsers) {
                try {
                    if (devicePolicyManager.isApplicationHidden(adminComponent, pkg)) count++
                } catch (_: Exception) {}
            }
            // Also count from known list
            for (pkg in KNOWN_BROWSERS) {
                if (browsers.contains(pkg)) continue // already counted above
                try {
                    context.packageManager.getPackageInfo(pkg, 0)
                    if (devicePolicyManager.isApplicationHidden(adminComponent, pkg)) count++
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "countBlockedBrowsers failed: ${e.message}")
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
            appendLine("LAYER 3: BROWSER BLOCKING (Dynamic)")
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
    val keywordDetectionActive : Boolean,
    val isFullyProtected       : Boolean
)

sealed class DnsTestResult {
    data class Success(val message: String) : DnsTestResult()
    data class Failed(val message: String)  : DnsTestResult()
    data class Error(val error: String)     : DnsTestResult()
}