package com.example.takwafortress.services.verification

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Service to verify if app names/packages exist on Google Play Store
 * Helps prevent typos when user manually enters app names
 */
class GooglePlayVerificationService(private val context: Context) {

    private val packageManager = context.packageManager

    /**
     * Searches Google Play Store for an app name and returns possible matches
     * @param appName The app name entered by user
     * @return List of possible package names with their display names
     */
    suspend fun searchPlayStore(appName: String): List<AppSearchResult> = withContext(Dispatchers.IO) {
        try {
            // First check common known apps
            val knownMatch = checkKnownApps(appName)
            if (knownMatch != null) {
                return@withContext listOf(knownMatch)
            }

            // For actual Play Store API, you'd need Google Play Developer API
            // For now, we'll use a simplified approach with known mappings
            val results = mutableListOf<AppSearchResult>()

            // Search through known apps database
            KNOWN_APPS.forEach { (pkg, details) ->
                if (details.name.contains(appName, ignoreCase = true) ||
                    details.alternateNames.any { it.contains(appName, ignoreCase = true) }) {
                    results.add(AppSearchResult(
                        packageName = pkg,
                        appName = details.name,
                        confidence = calculateConfidence(appName, details),
                        isInstalled = isAppInstalled(pkg)
                    ))
                }
            }

            results.sortedByDescending { it.confidence }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check if package name is valid format
     */
    fun isValidPackageName(packageName: String): Boolean {
        return packageName.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$"))
    }

    /**
     * Get app name from package name if installed
     */
    fun getAppNameFromPackage(packageName: String): String? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Check if app is installed
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
     * Check against known apps database
     */
    private fun checkKnownApps(query: String): AppSearchResult? {
        KNOWN_APPS.forEach { (pkg, details) ->
            if (details.name.equals(query, ignoreCase = true) ||
                details.alternateNames.any { it.equals(query, ignoreCase = true) }) {
                return AppSearchResult(
                    packageName = pkg,
                    appName = details.name,
                    confidence = 100,
                    isInstalled = isAppInstalled(pkg)
                )
            }
        }
        return null
    }

    /**
     * Calculate confidence score (0-100)
     */
    private fun calculateConfidence(query: String, details: AppDetails): Int {
        val queryLower = query.lowercase()
        val nameLower = details.name.lowercase()

        return when {
            nameLower == queryLower -> 100
            nameLower.startsWith(queryLower) -> 90
            nameLower.contains(queryLower) -> 70
            details.alternateNames.any { it.lowercase() == queryLower } -> 95
            details.alternateNames.any { it.lowercase().contains(queryLower) } -> 60
            else -> 40
        }
    }

    /**
     * Get all installed apps that can be blocked
     */
    fun getAllInstallableApps(): List<AppSearchResult> {
        val apps = packageManager.getInstalledApplications(0)
        return apps.mapNotNull { appInfo ->
            try {
                // Skip system critical apps
                if (isSystemCriticalApp(appInfo.packageName)) {
                    return@mapNotNull null
                }

                AppSearchResult(
                    packageName = appInfo.packageName,
                    appName = packageManager.getApplicationLabel(appInfo).toString(),
                    confidence = 100,
                    isInstalled = true
                )
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.appName }
    }

    /**
     * Check if app is system critical (shouldn't be blocked)
     */
    private fun isSystemCriticalApp(packageName: String): Boolean {
        return SYSTEM_CRITICAL_APPS.contains(packageName) ||
                packageName.startsWith("com.android.") ||
                packageName.startsWith("com.google.android.gms")
    }

    companion object {
        // System critical apps that should NEVER be blocked
        private val SYSTEM_CRITICAL_APPS = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.contacts",
            "com.google.android.gsf",
            "com.example.takwafortress" // Don't block ourselves!
        )

        // Known apps database (expand this as needed)
        private val KNOWN_APPS = mapOf(
            "com.twitter.android" to AppDetails(
                name = "X",
                alternateNames = listOf("Twitter", "X (Twitter)")
            ),
            "com.instagram.android" to AppDetails(
                name = "Instagram",
                alternateNames = listOf("IG", "Insta")
            ),
            "com.facebook.katana" to AppDetails(
                name = "Facebook",
                alternateNames = listOf("FB")
            ),
            "com.snapchat.android" to AppDetails(
                name = "Snapchat",
                alternateNames = listOf("Snap")
            ),
            "com.zhiliaoapp.musically" to AppDetails(
                name = "TikTok",
                alternateNames = listOf("Tik Tok")
            ),
            "org.telegram.messenger" to AppDetails(
                name = "Telegram",
                alternateNames = listOf()
            ),
            "com.discord" to AppDetails(
                name = "Discord",
                alternateNames = listOf()
            ),
            "com.reddit.frontpage" to AppDetails(
                name = "Reddit",
                alternateNames = listOf()
            ),
            "com.whatsapp" to AppDetails(
                name = "WhatsApp",
                alternateNames = listOf()
            ),
            "com.spotify.music" to AppDetails(
                name = "Spotify",
                alternateNames = listOf()
            ),
            "com.netflix.mediaclient" to AppDetails(
                name = "Netflix",
                alternateNames = listOf()
            ),
            "com.google.android.youtube" to AppDetails(
                name = "YouTube",
                alternateNames = listOf("Youtube", "YT")
            ),
            "com.google.android.apps.chrome" to AppDetails(
                name = "Chrome",
                alternateNames = listOf("Google Chrome")
            ),
            "org.mozilla.firefox" to AppDetails(
                name = "Firefox",
                alternateNames = listOf("Mozilla Firefox")
            ),
            "com.opera.browser" to AppDetails(
                name = "Opera",
                alternateNames = listOf("Opera Browser")
            ),
            "com.brave.browser" to AppDetails(
                name = "Brave",
                alternateNames = listOf("Brave Browser")
            ),
            "com.duckduckgo.mobile.android" to AppDetails(
                name = "DuckDuckGo",
                alternateNames = listOf("DuckDuckGo Browser", "DDG")
            )
        )
    }
}

/**
 * App details for known apps
 */
data class AppDetails(
    val name: String,
    val alternateNames: List<String>
)

/**
 * Search result from verification
 */
data class AppSearchResult(
    val packageName: String,
    val appName: String,
    val confidence: Int, // 0-100
    val isInstalled: Boolean
)