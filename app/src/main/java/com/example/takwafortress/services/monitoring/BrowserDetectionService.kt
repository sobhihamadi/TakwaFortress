package com.example.takwafortress.services.monitoring


import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import com.example.takwafortress.util.constants.BlockedPackages

class BrowserDetectionService(private val context: Context) {

    private val packageManager = context.packageManager

    /**
     * Detects all browser apps installed on device (including unknown ones).
     */
    fun detectAllBrowsers(): List<BrowserInfo> {
        val browsers = mutableListOf<BrowserInfo>()

        // Method 1: Check against known browser list (assuming it's a Map<String, String>)
        BlockedPackages.BROWSERS.keys.forEach { packageName ->
            if (isAppInstalled(packageName)) {
                browsers.add(
                    BrowserInfo(
                        packageName = packageName,
                        appName = getAppName(packageName),
                        isKnown = true,
                        isBlocked = true
                    )
                )
            }
        }

        // Method 2: Query all apps that can handle http:// URLs
        val unknownBrowsers = detectUnknownBrowsers()
        browsers.addAll(unknownBrowsers)

        return browsers.distinctBy { it.packageName }
    }

    /**
     * Detects browsers not in our known list (new or obscure browsers).
     */
    private fun detectUnknownBrowsers(): List<BrowserInfo> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"))
        val resolveInfoList: List<ResolveInfo> = packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )

        val unknownBrowsers = mutableListOf<BrowserInfo>()

        resolveInfoList.forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName

            // Skip if it's Chrome or already in the known list
            if (packageName != "com.android.chrome" && !BlockedPackages.BROWSERS.containsKey(packageName)) {
                unknownBrowsers.add(
                    BrowserInfo(
                        packageName = packageName,
                        appName = getAppName(packageName),
                        isKnown = false,
                        isBlocked = false
                    )
                )
            }
        }

        return unknownBrowsers
    }

    /**
     * Checks if a package is a browser app.
     */
    fun isBrowserApp(packageName: String): Boolean {
        // Check known list
        if (packageName in BlockedPackages.BROWSERS) return true

        // Check if it can handle http URLs
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"))
        intent.setPackage(packageName)

        val resolveInfo = packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )

        return resolveInfo != null
    }

    /**
     * Gets default browser on the device.
     */
    fun getDefaultBrowser(): String? {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"))
        val resolveInfo = packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )

        return resolveInfo?.activityInfo?.packageName
    }

    /**
     * Checks if Chrome is the default browser.
     */
    fun isChromeDefaultBrowser(): Boolean {
        return getDefaultBrowser() == "com.android.chrome"
    }

    /**
     * Gets browser detection report.
     */
    fun getBrowserReport(): BrowserReport {
        val allBrowsers = detectAllBrowsers()
        val knownBrowsers = allBrowsers.filter { it.isKnown }
        val unknownBrowsers = allBrowsers.filter { !it.isKnown }
        val defaultBrowser = getDefaultBrowser()

        return BrowserReport(
            totalBrowsers = allBrowsers.size,
            knownBrowsers = knownBrowsers,
            unknownBrowsers = unknownBrowsers,
            defaultBrowser = defaultBrowser,
            isChromeDefault = isChromeDefaultBrowser()
        )
    }

    /**
     * Gets browser threat level assessment.
     */
    fun assessThreatLevel(): BrowserThreatLevel {
        val report = getBrowserReport()

        return when {
            !report.isChromeDefault -> BrowserThreatLevel.Critical(
                "Default browser is not Chrome"
            )
            report.unknownBrowsers.isNotEmpty() -> BrowserThreatLevel.High(
                "${report.unknownBrowsers.size} unknown browser(s) detected"
            )
            report.knownBrowsers.size > 1 -> BrowserThreatLevel.Medium(
                "${report.knownBrowsers.size - 1} blocked browser(s) installed"
            )
            else -> BrowserThreatLevel.Low
        }
    }

    /**
     * Gets explanation of browser detection.
     */
    fun getBrowserDetectionExplanation(): String {
        val report = getBrowserReport()

        return """
            🔍 BROWSER DETECTION REPORT
            
            DEFAULT BROWSER: ${getAppName(report.defaultBrowser ?: "unknown")}
            ${if (report.isChromeDefault) "✅ Chrome is default" else "❌ Chrome is NOT default"}
            
            KNOWN BROWSERS (${report.knownBrowsers.size}):
            ${report.knownBrowsers.joinToString("\n") { "- ${it.appName} (${if (it.isBlocked) "BLOCKED" else "active"})" }}
            
            UNKNOWN BROWSERS (${report.unknownBrowsers.size}):
            ${if (report.unknownBrowsers.isEmpty()) "None detected ✅" else report.unknownBrowsers.joinToString("\n") { "⚠️ ${it.appName}" }}
            
            THREAT LEVEL: ${assessThreatLevel()}
            
            ${getThreatExplanation(assessThreatLevel())}
        """.trimIndent()
    }

    private fun getThreatExplanation(threatLevel: BrowserThreatLevel): String {
        return when (threatLevel) {
            is BrowserThreatLevel.Critical -> """
                🚨 CRITICAL: ${threatLevel.reason}
                ACTION REQUIRED: Set Chrome as default browser
            """.trimIndent()

            is BrowserThreatLevel.High -> """
                ⚠️ HIGH: ${threatLevel.reason}
                RECOMMENDATION: Uninstall unknown browsers
            """.trimIndent()

            is BrowserThreatLevel.Medium -> """
                ⚠️ MEDIUM: ${threatLevel.reason}
                STATUS: Blocked browsers are suspended
            """.trimIndent()

            BrowserThreatLevel.Low -> """
                ✅ LOW: All clear
                Only Chrome detected and active
            """.trimIndent()
        }
    }

    /**
     * Checks if app is installed.
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Gets app name from package.
     */
    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}

private fun PackageManager.getApplicationInfo(packageName: String?, flags: Int) {}

data class BrowserInfo(
    val packageName: String?,
    val appName: String?,
    val isKnown: Boolean,
    val isBlocked: Boolean
)

data class BrowserReport(
    val totalBrowsers: Int,
    val knownBrowsers: List<BrowserInfo>,
    val unknownBrowsers: List<BrowserInfo>,
    val defaultBrowser: String?,
    val isChromeDefault: Boolean
)

sealed class BrowserThreatLevel {
    data class Critical(val reason: String) : BrowserThreatLevel()
    data class High(val reason: String) : BrowserThreatLevel()
    data class Medium(val reason: String) : BrowserThreatLevel()
    object Low : BrowserThreatLevel()
}