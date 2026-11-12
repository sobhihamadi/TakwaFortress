package com.example.takwafortress.util.constants

/**
 * Central registry of package names the fortress knows about.
 *
 * Simplified: no packages are hard-blocked any more. Kept empty so older code compiles.
 */
object BlockedPackages {

    /** packageName → friendly display name */
    val BROWSERS: Map<String, String> = emptyMap()

    /** packageName → friendly display name */
    val NUCLEAR_BLACKLIST: Map<String, String> = emptyMap()

    /**
     * Returns a friendly name for any known package, or the raw package name as fallback.
     */
    fun getFriendlyName(packageName: String): String {
        return BROWSERS[packageName]
            ?: NUCLEAR_BLACKLIST[packageName]
            ?: packageName
    }

    /**
     * All known packages (browsers + nuclear) in one set – handy for quick lookup.
     */
    val ALL_BLOCKED: Set<String> = BROWSERS.keys + NUCLEAR_BLACKLIST.keys
}