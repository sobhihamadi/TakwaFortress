package com.example.takwafortress.util.constants

object AppConstants {

    // App Info
    const val APP_NAME = "Taqwa Fortress"
    const val APP_VERSION = "1.0.0"
    const val SUPPORT_EMAIL = "support@taqwafortress.com"
    const val WEBSITE_URL = "https://taqwafortress.com"

    // Pricing
    const val LICENSE_PRICE_USD = 17.0f
    const val CURRENCY = "USD"

    // License Format
    const val LICENSE_KEY_PREFIX = "TAQWA-"
    const val LICENSE_KEY_LENGTH = 19 // TAQWA-XXXX-XXXX-XXXX

    // Fortress Defaults
    const val DEFAULT_COMMITMENT_DAYS = 90
    const val MIN_COMMITMENT_DAYS = 30
    const val MAX_COMMITMENT_DAYS = 365

    // Time Intervals (milliseconds)
    const val ONE_SECOND = 1000L
    const val ONE_MINUTE = 60 * ONE_SECOND
    const val ONE_HOUR = 60 * ONE_MINUTE
    const val ONE_DAY = 24 * ONE_HOUR

    // SharedPreferences Keys
    const val PREFS_USER = "taqwa_user_prefs"
    const val PREFS_LICENSE = "taqwa_license_prefs"
    const val PREFS_FORTRESS = "taqwa_fortress_prefs"
    const val PREFS_BLOCKED_APPS = "taqwa_blocked_apps_prefs"
    const val PREFS_DEVICE_INFO = "taqwa_device_info_prefs"

    // Intent Actions
    const val ACTION_FORTRESS_ACTIVATED = "com.taqwa.fortress.FORTRESS_ACTIVATED"
    const val ACTION_FORTRESS_EXPIRED = "com.taqwa.fortress.FORTRESS_EXPIRED"
    const val ACTION_BLOCKED_APP_DETECTED = "com.taqwa.fortress.BLOCKED_APP_DETECTED"

    // Notification IDs
    const val NOTIFICATION_ID_FORTRESS_ACTIVE = 1001
    const val NOTIFICATION_ID_APP_BLOCKED = 1002
    const val NOTIFICATION_ID_PERIOD_EXPIRED = 1003

    // Notification Channels
    const val CHANNEL_ID_FORTRESS = "fortress_channel"
    const val CHANNEL_ID_VIOLATIONS = "violations_channel"
    const val CHANNEL_ID_SYSTEM = "system_channel"

    // Request Codes
    const val REQUEST_CODE_DEVICE_ADMIN = 2001
    const val REQUEST_CODE_ACCESSIBILITY = 2002
    const val REQUEST_CODE_WIRELESS_ADB = 2003

    // Feature Flags
    const val ENABLE_KNOX_ACTIVATION = true
    const val ENABLE_WIRELESS_ADB = true
    const val ENABLE_WEBVIEW_KILLER = true
    const val ENABLE_SAFE_MODE_BLOCKER = true

    // Logging
    const val LOG_TAG = "TaqwaFortress"
    const val ENABLE_DEBUG_LOGGING = true

    // URLs
    const val TERMS_URL = "$WEBSITE_URL/terms"
    const val PRIVACY_URL = "$WEBSITE_URL/privacy"
    const val FAQ_URL = "$WEBSITE_URL/faq"
    const val SETUP_GUIDE_URL = "$WEBSITE_URL/setup"

    // Contact
    const val TELEGRAM_SUPPORT = "https://t.me/taqwa_support"
    const val DISCORD_SUPPORT = "https://discord.gg/taqwa"

    // Messages
    const val MESSAGE_FACTORY_RESET_WARNING = """
        ⚠️ WARNING ⚠️
        
        Factory reset will:
        - Revoke your $17 license
        - Require new purchase to reactivate
        - Reset all progress
        
        Are you sure?
    """

    const val MESSAGE_COMMITMENT_COMPLETE = """
        🎉 CONGRATULATIONS! 🎉
        
        You completed your commitment period!
        Your brain has been rewired.
        
        What's next?
        - Continue protection (start new period)
        - Take a break (disable fortress)
        
        We're proud of you.
    """
}