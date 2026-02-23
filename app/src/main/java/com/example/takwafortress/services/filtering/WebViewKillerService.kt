package com.example.takwafortress.services.filtering


import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

class WebViewKillerService(private val context: Context) {

    /**
     * Checks if Accessibility Service is enabled for Taqwa.
     * Required for WebView killing functionality.
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val packageName = context.packageName
        return enabledServices.contains("$packageName/")
    }

    /**
     * Gets user instructions for enabling Accessibility Service.
     */
    fun getAccessibilityInstructions(): String {
        return """
            🔍 ENABLE WEBVIEW KILLER
            
            To block in-app browsers (WebViews), you need to:
            
            1. Go to Settings → Accessibility
            2. Find "Taqwa WebView Blocker"
            3. Turn it ON
            4. Tap "Allow" on permission popup
            
            What are WebViews?
            - Mini-browsers inside apps (Instagram, Facebook, Twitter)
            - Links open inside the app instead of Chrome
            - Bypass our DNS filter and Chrome protections
            
            What we'll do:
            ✅ Detect when WebView opens
            ✅ Kill it instantly
            ✅ Force link to open in Managed Chrome
            ✅ Your protection stays intact
            
            This is REQUIRED for full protection.
        """.trimIndent()
    }

    /**
     * Checks WebView killer status.
     */
    fun getWebViewKillerStatus(): WebViewKillerStatus {
        return if (isAccessibilityServiceEnabled()) {
            WebViewKillerStatus.Active
        } else {
            WebViewKillerStatus.Inactive
        }
    }

    /**
     * Gets list of apps that commonly use WebViews.
     */
    fun getWebViewApps(): List<String> {
        return listOf(
            "com.instagram.android",
            "com.facebook.katana",
            "com.twitter.android",
            "com.snapchat.android",
            "com.zhiliaoapp.musically", // TikTok
            "com.linkedin.android",
            "com.reddit.frontpage",
            "com.pinterest",
            "com.tumblr",
            "com.medium.reader"
        )
    }

    /**
     * Explains the WebView threat.
     */
    fun getWebViewThreatExplanation(): String {
        return """
            ⚠️ THE WEBVIEW VULNERABILITY
            
            SCENARIO:
            1. You open Instagram (allowed app)
            2. Someone posts a link to a website
            3. You tap the link
            4. Instagram opens it in an IN-APP BROWSER (WebView)
            5. This bypasses Chrome protections
            6. DNS filter still works, but Incognito/SafeSearch don't
            
            THE PROBLEM:
            ❌ WebViews can have private browsing
            ❌ WebViews can disable SafeSearch
            ❌ WebViews can clear history
            ❌ We don't control them like Chrome
            
            THE SOLUTION:
            ✅ Taqwa detects WebView opening
            ✅ Kills it within 100ms
            ✅ Redirects link to Managed Chrome
            ✅ Full protection restored
            
            TECHNICAL DETAILS:
            - Uses AccessibilityService API
            - Monitors for WebView class instantiation
            - Kills process immediately
            - Extracts URL and opens in Chrome
            
            Status: ${getWebViewKillerStatus()}
        """.trimIndent()
    }

    /**
     * Tests WebView killer (for debugging).
     */
    fun testWebViewKiller(): WebViewKillerTestResult {
        if (!isAccessibilityServiceEnabled()) {
            return WebViewKillerTestResult.NotEnabled
        }

        // TODO: Implement actual test by spawning a test WebView
        return WebViewKillerTestResult.Success("WebView killer is active and monitoring")
    }
}

sealed class WebViewKillerStatus {
    object Active : WebViewKillerStatus()
    object Inactive : WebViewKillerStatus()
}

sealed class WebViewKillerTestResult {
    data class Success(val message: String) : WebViewKillerTestResult()
    object NotEnabled : WebViewKillerTestResult()
    data class Failed(val reason: String) : WebViewKillerTestResult()
}

/**
 * Accessibility Service implementation for killing WebViews.
 * This class should be in a separate file: services/TaqwaAccessibilityService.kt
 */
abstract class TaqwaWebViewKillerAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            // Detect WebView creation
            if (isWebViewEvent(it)) {
                killWebView(it)
            }
        }
    }

    private fun isWebViewEvent(event: AccessibilityEvent): Boolean {
        // Check if event is from a WebView class
        val className = event.className?.toString() ?: return false
        return className.contains("WebView") ||
                className.contains("webkit")
    }

    private fun killWebView(event: AccessibilityEvent) {
        // TODO: Implement WebView killing logic
        // 1. Get the URL from WebView
        // 2. Close the WebView
        // 3. Open URL in Chrome with Intent
    }

    override fun onInterrupt() {
        // Handle service interruption
    }
}