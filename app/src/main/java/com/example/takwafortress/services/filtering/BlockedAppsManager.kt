package com.example.takwafortress.services.filtering

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Manages the custom blocked apps list.
 * Users can add/remove apps from the block list.
 */
class BlockedAppsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "blocked_apps_config",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
    }

    /**
     * Gets the list of blocked package names.
     */
    fun getBlockedPackages(): Set<String> {
        val jsonString = prefs.getString(KEY_BLOCKED_PACKAGES, null) ?: return emptySet()

        return try {
            val jsonArray = JSONArray(jsonString)
            val packages = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                packages.add(jsonArray.getString(i))
            }
            packages
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * Adds a package to the blocked list.
     */
    fun addBlockedPackage(packageName: String) {
        val current = getBlockedPackages().toMutableSet()
        current.add(packageName)
        saveBlockedPackages(current)
    }

    /**
     * Removes a package from the blocked list.
     */
    fun removeBlockedPackage(packageName: String) {
        val current = getBlockedPackages().toMutableSet()
        current.remove(packageName)
        saveBlockedPackages(current)
    }

    /**
     * Checks if a package is blocked.
     */
    fun isPackageBlocked(packageName: String): Boolean {
        return getBlockedPackages().contains(packageName)
    }

    /**
     * Saves the blocked packages list.
     */
    private fun saveBlockedPackages(packages: Set<String>) {
        val jsonArray = JSONArray()
        packages.forEach { jsonArray.put(it) }

        prefs.edit()
            .putString(KEY_BLOCKED_PACKAGES, jsonArray.toString())
            .apply()
    }

    /**
     * Initializes with default blocked apps (first time only).
     */
    fun initializeDefaults() {
        if (getBlockedPackages().isEmpty()) {
            // Add default nuclear apps
            val defaults = setOf(
                "org.telegram.messenger",
                "com.reddit.frontpage",
                "com.twitter.android"
            )
            saveBlockedPackages(defaults)
        }
    }

    /**
     * Gets all installed apps that CAN be blocked.
     */
    fun getBlockableApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)

        return apps.mapNotNull { app ->
            try {
                AppInfo(
                    packageName = app.packageName,
                    appName = pm.getApplicationLabel(app).toString(),
                    isBlocked = isPackageBlocked(app.packageName)
                )
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.appName }
    }
}

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean
)