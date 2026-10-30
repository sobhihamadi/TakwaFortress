package com.example.takwafortress.ui.fragments

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.takwafortress.services.filtering.SiteAllowlistManager
import com.example.takwafortress.services.filtering.SiteBlockingService
import com.example.takwafortress.services.filtering.SiteBlockResult
import kotlinx.coroutines.launch

// ── Palette (matches the rest of the app) ─────────────────────────────────────
private val BG_DARK    = Color.parseColor("#161B27")
private val CARD_BG    = Color.parseColor("#1E2535")
private val INPUT_BG   = Color.parseColor("#141929")
private val BORDER_DIM = Color.parseColor("#2A3347")
private val BORDER_TOP = Color.parseColor("#354059")
private val BLUE       = Color.parseColor("#4A90D9")
private val GREEN      = Color.parseColor("#5DB88A")
private val GREEN_DIM  = Color.parseColor("#1A3040")
private val ORANGE     = Color.parseColor("#E0A458")
private val ORANGE_DIM = Color.parseColor("#2A1E0E")
private val RED        = Color.parseColor("#E05C5C")
private val RED_DIM    = Color.parseColor("#2E1A1A")
private val TEXT_WHITE = Color.parseColor("#EFF3F8")
private val TEXT_SOFT  = Color.parseColor("#D4DCE8")
private val TEXT_GREY  = Color.parseColor("#7A8BA0")
private val DIVIDER    = Color.parseColor("#252E3F")

class SitesFragment : Fragment() {

    private lateinit var siteBlockingService: SiteBlockingService
    private lateinit var allowlist: SiteAllowlistManager
    private lateinit var blockedListContainer: LinearLayout
    private lateinit var urlInput: EditText
    private lateinit var blockButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    companion object {
        fun newInstance() = SitesFragment()
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        siteBlockingService = SiteBlockingService(requireContext())
        allowlist = SiteAllowlistManager(requireContext())
        return buildUi()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            if (allowlist.needsApply()) siteBlockingService.applyToChrome()
            loadBlockedSites()
        }
    }

    // ── Build UI ───────────────────────────────────────────────────────────────

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(BG_DARK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), getStatusBarHeight() + dp(12), dp(16), dp(32))
        }

        // ── Header ─────────────────────────────────────────────────────────────
        content.addView(text("TAKWA FORTRESS", 10f, TEXT_GREY, letterSpacing = 0.25f, gravity = Gravity.CENTER))
        content.addView(text("Allowed Sites", 20f, TEXT_WHITE, bold = true, gravity = Gravity.CENTER).apply {
            setPadding(0, dp(4), 0, dp(6))
        })
        content.addView(text("Chrome opens only the sites listed here during your commitment", 13f, TEXT_GREY, gravity = Gravity.CENTER).apply {
            setPadding(0, 0, 0, dp(24))
        })

        // ── Info banner ────────────────────────────────────────────────────────
        val banner = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = lp(bottomMargin = dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#172540"))
                setStroke(dp(1), Color.parseColor("#1F3554"))
                cornerRadius = dp(10).toFloat()
            }
        }
        banner.addView(text("🌐  How it works", 13f, BLUE, bold = true).apply { setPadding(0, 0, 0, dp(6)) })
        banner.addView(text(
            "Once your fortress is active, Chrome blocks every site except the ones allowed here. " +
                    "A site you add after activation waits 12 hours and needs your Approve before it works. " +
                    "Add the sites you need now, before activating. google.com is always allowed. " +
                    "Enter a domain like github.com — no need to include http:// or www.",
            12f, TEXT_GREY
        ).apply { setLineSpacing(0f, 1.4f) })
        content.addView(banner)

        // ── Add-site card ──────────────────────────────────────────────────────
        val addCard = card()

        addCard.addView(text("➕  ADD SITE TO ALLOW", 11f, TEXT_GREY, letterSpacing = 0.12f).apply {
            setPadding(0, 0, 0, dp(14))
        })

        urlInput = EditText(requireContext()).apply {
            hint = "e.g. github.com"
            setTextColor(TEXT_WHITE)
            setHintTextColor(TEXT_GREY)
            textSize = 14f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(INPUT_BG)
                setStroke(dp(1), BORDER_DIM)
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = lp(height = dp(50), bottomMargin = dp(10))
        }
        addCard.addView(urlInput)

        blockButton = Button(requireContext()).apply {
            text = "Allow Site"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(BLUE)
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = lp(height = dp(52))
        }
        addCard.addView(blockButton)

        progressBar = ProgressBar(requireContext()).apply {
            visibility = View.GONE
            indeterminateTintList = android.content.res.ColorStateList.valueOf(BLUE)
            layoutParams = lp().apply { gravity = Gravity.CENTER; topMargin = dp(8) }
        }
        addCard.addView(progressBar)

        statusText = text("", 13f, TEXT_GREY).apply {
            visibility = View.GONE
            setPadding(0, dp(6), 0, 0)
        }
        addCard.addView(statusText)
        content.addView(addCard)

        // ── Blocked list ───────────────────────────────────────────────────────
        blockedListContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = lp()
        }
        content.addView(blockedListContainer)

        // ── Listeners ──────────────────────────────────────────────────────────
        blockButton.setOnClickListener { handleBlockSite() }
        urlInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                statusText.visibility = View.GONE
            }
        })

        scroll.addView(content)
        return scroll
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    private fun handleBlockSite() {
        val input = urlInput.text.toString().trim()
        if (input.isBlank()) {
            showStatus("Please enter a domain", TEXT_GREY)
            return
        }
        progressBar.visibility = View.VISIBLE
        blockButton.isEnabled = false
        statusText.visibility = View.GONE

        lifecycleScope.launch {
            val result = siteBlockingService.allowSite(input)
            progressBar.visibility = View.GONE
            blockButton.isEnabled = true
            when (result) {
                is SiteBlockResult.Success -> {
                    urlInput.text.clear()
                    if (allowlist.isEnforced())
                        showStatus("⏳  ${result.domain} is waiting — approve it below, it works after 12 hours", ORANGE)
                    else
                        showStatus("✅  ${result.domain} is allowed in Chrome", GREEN)
                    loadBlockedSites()
                }
                is SiteBlockResult.InvalidUrl ->
                    showStatus("⚠️  Enter a valid domain (e.g. reddit.com)", RED)
                is SiteBlockResult.DeviceOwnerRequired ->
                    showStatus("❌  Device Owner required to manage sites", RED)
                is SiteBlockResult.ApplyFailed ->
                    showStatus("⚠️  Site saved but Chrome policy update failed", RED)
            }
        }
    }

    private fun confirmRemove(site: SiteAllowlistManager.AllowedSite) {
        AlertDialog.Builder(requireContext())
            .setTitle(if (site.isActive()) "Remove ${site.domain}?" else "Cancel ${site.domain}?")
            .setMessage(
                if (site.isActive()) "Chrome will stop opening this site. Adding it back later waits 12 hours."
                else "This site will not be allowed."
            )
            .setPositiveButton(if (site.isActive()) "Remove" else "Cancel request") { _, _ ->
                lifecycleScope.launch {
                    siteBlockingService.removeAllowedSite(site.domain)
                    loadBlockedSites()
                    showStatus("${site.domain} removed", TEXT_GREY)
                }
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun loadBlockedSites() {
        lifecycleScope.launch {
            val sites = siteBlockingService.getAllowedSites()
            blockedListContainer.removeAllViews()

            if (sites.isEmpty()) {
                blockedListContainer.addView(
                    // Replace the empty-state text inside loadBlockedSites():
                    text(
                        "No sites allowed yet\n\nAdd the sites you need in Chrome.\nAnything not listed stays blocked.",
                        14f, TEXT_GREY, gravity = Gravity.CENTER
                    ).apply {
                        setPadding(0, dp(32), 0, 0)
                        setLineSpacing(0f, 1.5f)
                        layoutParams = lp()
                    }
                )
                return@launch
            }

            val countLabel = text("${sites.size} site${if (sites.size == 1) "" else "s"} allowed or pending", 11f, TEXT_GREY, letterSpacing = 0.1f)
            countLabel.setPadding(0, 0, 0, dp(10))
            blockedListContainer.addView(countLabel)

            sites.forEach { site -> blockedListContainer.addView(siteRow(site)) }
        }
    }

    // ── Row factory ────────────────────────────────────────────────────────────

    private fun siteRow(site: SiteAllowlistManager.AllowedSite): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = lp(bottomMargin = dp(6))
            background = cardDrawable(CARD_BG, BORDER_DIM)
        }

        val textBlock = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textBlock.addView(text(site.domain, 15f, TEXT_WHITE, bold = true))
        val totalMin = (site.remainingMs() + 59_999) / 60_000
        val left = "${totalMin / 60}h ${totalMin % 60}m"
        val pill = when {
            site.isActive()      -> statusPill("● Allowed in Chrome", GREEN, GREEN_DIM)
            site.approved        -> statusPill("✔ Approved · works in $left", GREEN, GREEN_DIM)
            totalMin > 0         -> statusPill("⏳ Blocked · $left left", ORANGE, ORANGE_DIM)
            else                 -> statusPill("⏳ Blocked · waiting for your decision", ORANGE, ORANGE_DIM)
        }
        textBlock.addView(pill.apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(4)
        })

        fun actionPill(label: String, color: Int, bg: Int, onClick: () -> Unit) = TextView(requireContext()).apply {
            text = label
            textSize = 12f
            setTextColor(color)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(bg); cornerRadius = dp(20).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
            setOnClickListener { onClick() }
        }

        val actions = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(8) }
            if (!site.approved) {
                addView(actionPill("Approve", GREEN, GREEN_DIM) {
                    lifecycleScope.launch {
                        siteBlockingService.approveSite(site.domain)
                        loadBlockedSites()
                        showStatus("✅  ${site.domain} approved", GREEN)
                    }
                })
                addView(actionPill("Reject", RED, RED_DIM) {
                    lifecycleScope.launch {
                        siteBlockingService.removeAllowedSite(site.domain)
                        loadBlockedSites()
                        showStatus("🚫  ${site.domain} rejected", TEXT_GREY)
                    }
                })
            } else {
                addView(actionPill(if (site.isActive()) "Remove" else "Cancel", RED, RED_DIM) { confirmRemove(site) })
            }
        }

        row.addView(textBlock)
        row.addView(actions)
        return row
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────

    private fun showStatus(msg: String, color: Int) {
        statusText.text = msg
        statusText.setTextColor(color)
        statusText.visibility = View.VISIBLE
    }

    private fun card() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = lp(bottomMargin = dp(10))
        background = cardDrawable(CARD_BG, BORDER_DIM)
    }

    private fun statusPill(label: String, textColor: Int, bgColor: Int) =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(3), dp(10), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(bgColor)
                cornerRadius = dp(20).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(text(label, 10f, textColor, bold = true))
        }

    private fun cardDrawable(fill: Int, border: Int): LayerDrawable {
        val base = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill); setStroke(dp(1), border); cornerRadius = dp(12).toFloat()
        }
        val topLine = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; setColor(BORDER_TOP); cornerRadius = dp(12).toFloat()
        }
        return LayerDrawable(arrayOf(base, topLine)).apply {
            setLayerInset(1, dp(1), dp(1), dp(1), 0)
            setLayerHeight(1, dp(1))
            setLayerGravity(1, Gravity.TOP)
        }
    }

    private fun text(
        s: String, size: Float, color: Int,
        bold: Boolean = false, gravity: Int = Gravity.START,
        letterSpacing: Float = 0f
    ) = TextView(requireContext()).apply {
        text = s; textSize = size; setTextColor(color)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        this.gravity = gravity
        if (letterSpacing != 0f) this.letterSpacing = letterSpacing
    }

    private fun lp(height: Int = LinearLayout.LayoutParams.WRAP_CONTENT, bottomMargin: Int = 0) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height).apply {
            this.bottomMargin = bottomMargin
        }

    private fun getStatusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}