package com.example.takwafortress.services.filtering

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility Service — Keyword Detection Layer
 *
 * Monitors Chrome's address bar (and search bar) for blocked keywords.
 * When a match is detected:
 *   1. Clears the address bar text via AccessibilityAction
 *   2. Shows a fullscreen warning overlay (KeywordWarningOverlay)
 *
 * Activated automatically by DevicePolicyManager when the user taps
 * "Activate Full Protection" — no Settings screen needed.
 *
 * Watches ONLY: com.android.chrome
 * Events  ONLY: TYPE_VIEW_TEXT_CHANGED, TYPE_WINDOW_CONTENT_CHANGED
 */
class KeywordAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG             = "KeywordA11yService"
        const val CHROME_PACKAGE          = "com.android.chrome"

        // Node class names that represent the address / search bar in Chrome
        private val ADDRESS_BAR_CLASSES = setOf(
            "android.widget.EditText",
            "android.widget.AutoCompleteTextView"
        )

        // Minimum text length to start checking (avoids single-letter false-hits)
        private const val MIN_CHECK_LENGTH = 3

        // Cooldown between clears (ms) — prevents flickering on rapid keystrokes
        private const val CLEAR_COOLDOWN_MS = 1_500L
    }

    private lateinit var keywordsManager : BlockedKeywordsManager
    private lateinit var warningOverlay  : KeywordWarningOverlay

    private var lastClearTimestamp = 0L

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        keywordsManager = BlockedKeywordsManager(applicationContext)
        warningOverlay  = KeywordWarningOverlay(applicationContext)

        // Configure which events and packages to listen to
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes =
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageNames  = arrayOf(CHROME_PACKAGE)
            feedbackType  = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags         =
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100  // ms — responsive but not too aggressive
        }

        Log.d(TAG, "✅ KeywordAccessibilityService connected — monitoring Chrome")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Only care about Chrome
        if (event.packageName?.toString() != CHROME_PACKAGE) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                // Most direct: fired when user types in any EditText
                val text = event.text?.joinToString("") ?: return
                checkAndBlock(text, event.source)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Fallback: scan the whole window for address bar text
                scanWindowForAddressBar(rootInActiveWindow)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "KeywordAccessibilityService interrupted")
        warningOverlay.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        warningOverlay.dismiss()
        Log.d(TAG, "KeywordAccessibilityService destroyed")
    }

    // ── Core logic ────────────────────────────────────────────────────────────

    /**
     * Checks [text] against the blocked keywords list.
     * If a match is found and cooldown has passed:
     *   - Clears the node's text
     *   - Shows the warning overlay
     */
    private fun checkAndBlock(text: String, sourceNode: AccessibilityNodeInfo?) {
        if (text.length < MIN_CHECK_LENGTH) return

        val matchedWord = keywordsManager.findMatch(text) ?: return

        Log.w(TAG, "🚨 Blocked keyword detected: \"$matchedWord\" in text: \"$text\"")

        val now = System.currentTimeMillis()
        if (now - lastClearTimestamp < CLEAR_COOLDOWN_MS) return   // still in cooldown

        lastClearTimestamp = now

        // 1. Clear the address bar
        clearNode(sourceNode)

        // 2. Also try to clear via full window scan (belt + suspenders)
        clearAddressBarInWindow(rootInActiveWindow)

        // 3. Show warning overlay
        warningOverlay.show(matchedWord)

        Log.d(TAG, "✅ Address bar cleared. Overlay shown.")
    }

    /**
     * Walks the accessibility node tree to find Chrome's address bar EditText,
     * reads its text, and calls checkAndBlock on it.
     */
    private fun scanWindowForAddressBar(root: AccessibilityNodeInfo?) {
        root ?: return
        traverseNodes(root) { node ->
            if (node.className?.toString() in ADDRESS_BAR_CLASSES && node.isEditable) {
                val nodeText = node.text?.toString() ?: ""
                if (nodeText.length >= MIN_CHECK_LENGTH) {
                    checkAndBlock(nodeText, node)
                }
            }
        }
    }

    /**
     * Finds all editable address-bar nodes and clears them.
     */
    private fun clearAddressBarInWindow(root: AccessibilityNodeInfo?) {
        root ?: return
        traverseNodes(root) { node ->
            if (node.className?.toString() in ADDRESS_BAR_CLASSES && node.isEditable) {
                clearNode(node)
            }
        }
    }

    /**
     * Clears text in [node] using AccessibilityAction SET_TEXT.
     * Falls back to selecting all + DELETE if SET_TEXT isn't supported.
     */
    private fun clearNode(node: AccessibilityNodeInfo?) {
        node ?: return
        try {
            // Primary method: SET_TEXT with empty string
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    ""
                )
            }
            val cleared = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

            if (!cleared) {
                // Fallback: select all then cut
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                node.performAction(AccessibilityNodeInfo.ACTION_SELECT)
                val cutArgs = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, ""
                    )
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, cutArgs)
            }

            Log.d(TAG, "Node cleared: $cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear node: ${e.message}")
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Depth-first traversal of accessibility node tree.
     * Recycles nodes after visiting to avoid memory leaks.
     */
    private fun traverseNodes(
        node: AccessibilityNodeInfo,
        visitor: (AccessibilityNodeInfo) -> Unit
    ) {
        visitor(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNodes(child, visitor)
            child.recycle()
        }
    }
}