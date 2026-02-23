package com.example.takwafortress.util.constants

/**
 * Central registry of package names the fortress knows about.
 *
 * BROWSERS      – suspended (greyed out, still visible in app drawer)
 * NUCLEAR_BLACKLIST – hidden completely (invisible in launcher)
 */
object BlockedPackages {

    /** packageName → friendly display name */
    val BROWSERS: Map<String, String> = mapOf(
        "com.opera.browser"                     to "Opera Browser",
        "com.opera.browser.preinstall"          to "Opera Browser (Pre-installed)",
        "org.mozilla.firefox"                   to "Firefox",
        "com.microsoft.teams"                   to "Microsoft Edge",
        "com.microsoft.launcher"                to "Microsoft Edge (Launcher)",
        "com.duckduckgo.android.core"           to "DuckDuckGo Browser",
        "com.brave.browser"                     to "Brave Browser",
        "com.google.android.apps.chrome"        to "Chrome (duplicate check)",
        "com.sec.android.app.samsunginternet"   to "Samsung Internet"
    )

    /** packageName → friendly display name */
    val NUCLEAR_BLACKLIST: Map<String, String> = mapOf(
        "org.telegram.messenger"                to "Telegram",
        "com.reddit.client"                     to "Reddit",
        "com.twitter.android"                   to "X (Twitter)",
        "com.discord"                           to "Discord"
    )

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