package com.example.takwafortress.services.filtering

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Draws a fullscreen warning overlay (TYPE_APPLICATION_OVERLAY) when a
 * blocked keyword is detected in Chrome's address bar.
 *
 * Requires: android.permission.SYSTEM_ALERT_WINDOW  — NOT needed because
 * we use TYPE_ACCESSIBILITY_OVERLAY from within the AccessibilityService,
 * which does not require that permission.
 *
 * Auto-dismisses after [AUTO_DISMISS_MS] milliseconds.
 */
class KeywordWarningOverlay(private val context: Context) {

    companion object {
        private const val AUTO_DISMISS_MS = 4_000L   // 4 seconds

        // ── Palette (mirrors DashboardFragment) ──────────────────────────────
        private val BG_DARK     = Color.parseColor("#161B27")
        private val CARD_BG     = Color.parseColor("#1E2535")
        private val CARD_BORDER = Color.parseColor("#2A3347")
        private val BLUE        = Color.parseColor("#4A90D9")
        private val ORANGE      = Color.parseColor("#D4924A")
        private val ORANGE_DIM  = Color.parseColor("#2A1E0E")
        private val TEXT_WHITE  = Color.parseColor("#EFF3F8")
        private val TEXT_SOFT   = Color.parseColor("#D4DCE8")
        private val TEXT_GREY   = Color.parseColor("#7A8BA0")
        private val GREEN       = Color.parseColor("#5DB88A")
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { dismiss() }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Shows the warning overlay for [matchedKeyword].
     * Safe to call from any thread.
     * If overlay is already visible, resets the auto-dismiss timer.
     */
    fun show(matchedKeyword: String) {
        handler.post {
            // Already showing — just reset timer
            if (overlayView != null) {
                handler.removeCallbacks(dismissRunnable)
                handler.postDelayed(dismissRunnable, AUTO_DISMISS_MS)
                return@post
            }

            val view = buildOverlayView(matchedKeyword)
            val params = buildLayoutParams()

            try {
                windowManager.addView(view, params)
                overlayView = view
                handler.postDelayed(dismissRunnable, AUTO_DISMISS_MS)
            } catch (e: Exception) {
                android.util.Log.e("KeywordWarningOverlay", "Failed to add overlay: ${e.message}")
            }
        }
    }

    /** Dismisses the overlay immediately. */
    fun dismiss() {
        handler.post {
            handler.removeCallbacks(dismissRunnable)
            overlayView?.let {
                try { windowManager.removeView(it) } catch (_: Exception) {}
                overlayView = null
            }
        }
    }

    // ── Build overlay view ────────────────────────────────────────────────────

    private fun buildOverlayView(matchedKeyword: String): View {
        val ctx = context

        // Root — full screen, dark background
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(230, 22, 27, 39)) // BG_DARK @ 90% opacity
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Card
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(32), dp(28), dp(28))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(CARD_BG)
                setStroke(dp(1), CARD_BORDER)
                cornerRadius = dp(16).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin  = dp(24)
                rightMargin = dp(24)
            }
        }

        // Shield emoji
        card.addView(TextView(ctx).apply {
            text = "🛡️"
            textSize = 48f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        })

        // Title
        card.addView(TextView(ctx).apply {
            text = "TAKWA FORTRESS"
            textSize = 11f
            setTextColor(TEXT_GREY)
            gravity = Gravity.CENTER
            letterSpacing = 0.25f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        })

        // Warning headline
        card.addView(TextView(ctx).apply {
            text = "Search Blocked"
            textSize = 22f
            setTextColor(ORANGE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        })

        // Divider
        card.addView(View(ctx).apply {
            setBackgroundColor(Color.parseColor("#252E3F"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { bottomMargin = dp(16) }
        })

        // Islamic reminder
        card.addView(TextView(ctx).apply {
            text = "وَاللَّهُ يَعْلَمُ مَا تُسِرُّونَ وَمَا تُعْلِنُونَ"
            textSize = 16f
            setTextColor(TEXT_WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        })

        card.addView(TextView(ctx).apply {
            text = "\"Allah knows what you conceal and what you reveal.\"\n— Quran 16:19"
            textSize = 12f
            setTextColor(TEXT_GREY)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(20) }
        })

        // Motivational message
        card.addView(TextView(ctx).apply {
            text = "You committed to protecting yourself.\nStay strong. This search has been cleared."
            textSize = 13f
            setTextColor(TEXT_SOFT)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(20) }
        })

        // Progress pill — "Returning to safety in Xs"
        val pillBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#1A3040"))
            cornerRadius = dp(20).toFloat()
        }
        card.addView(TextView(ctx).apply {
            text = "✅  Returning to safety in ${AUTO_DISMISS_MS / 1000}s…"
            textSize = 12f
            setTextColor(GREEN)
            gravity = Gravity.CENTER
            background = pillBg
            setPadding(dp(16), dp(8), dp(16), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })

        root.addView(card)

        // Tap anywhere to dismiss early
        root.setOnClickListener { dismiss() }

        return root
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()
}