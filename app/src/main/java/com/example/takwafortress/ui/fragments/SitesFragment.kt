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
import com.example.takwafortress.model.entities.BlockedSite
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
private val RED        = Color.parseColor("#E05C5C")
private val RED_DIM    = Color.parseColor("#2E1A1A")
private val TEXT_WHITE = Color.parseColor("#EFF3F8")
private val TEXT_SOFT  = Color.parseColor("#D4DCE8")
private val TEXT_GREY  = Color.parseColor("#7A8BA0")
private val DIVIDER    = Color.parseColor("#252E3F")

class SitesFragment : Fragment() {

    private lateinit var siteBlockingService: SiteBlockingService
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
        return buildUi()
    }

    override fun onResume() {
        super.onResume()
        loadBlockedSites()
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
        content.addView(text("Site Blocking", 20f, TEXT_WHITE, bold = true, gravity = Gravity.CENTER).apply {
            setPadding(0, dp(4), 0, dp(6))
        })
        content.addView(text("Block websites in Chrome during your commitment", 13f, TEXT_GREY, gravity = Gravity.CENTER).apply {
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
            "Sites you add here are blocked in Chrome immediately via the URLBlocklist policy. " +
                    "DNS blocking also applies device-wide. Enter a domain like reddit.com or instagram.com — " +
                    "no need to include http:// or www.",
            12f, TEXT_GREY
        ).apply { setLineSpacing(0f, 1.4f) })
        content.addView(banner)

        // ── Add-site card ──────────────────────────────────────────────────────
        val addCard = card()

        addCard.addView(text("➕  ADD SITE TO BLOCK", 11f, TEXT_GREY, letterSpacing = 0.12f).apply {
            setPadding(0, 0, 0, dp(14))
        })

        urlInput = EditText(requireContext()).apply {
            hint = "e.g. reddit.com or instagram.com"
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
            text = "Block Site"
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
            val result = siteBlockingService.blockSite(input)
            progressBar.visibility = View.GONE
            blockButton.isEnabled = true
            when (result) {
                is SiteBlockResult.Success -> {
                    urlInput.text.clear()
                    showStatus("✅  ${result.domain} is now blocked in Chrome", GREEN)
                    loadBlockedSites()
                }
                is SiteBlockResult.InvalidUrl ->
                    showStatus("⚠️  Enter a valid domain (e.g. reddit.com)", RED)
                is SiteBlockResult.DeviceOwnerRequired ->
                    showStatus("❌  Device Owner required to block sites", RED)
                is SiteBlockResult.ApplyFailed ->
                    showStatus("⚠️  Site saved but Chrome policy update failed", RED)
            }
        }
    }

    private fun confirmUnblock(site: BlockedSite) {
        AlertDialog.Builder(requireContext())
            .setTitle("Unblock ${site.domain}?")
            .setMessage("This removes the Chrome URLBlocklist entry for this site. DNS blocking may still apply.")
            .setPositiveButton("Unblock") { _, _ ->
                lifecycleScope.launch {
                    siteBlockingService.unblockSite(site.id)
                    loadBlockedSites()
                    showStatus("${site.domain} unblocked", TEXT_GREY)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadBlockedSites() {
        lifecycleScope.launch {
            val sites = siteBlockingService.getAllBlockedSites()
            blockedListContainer.removeAllViews()

            if (sites.isEmpty()) {
                blockedListContainer.addView(
                    // Replace the empty-state text inside loadBlockedSites():
                    text(
                        "No sites blocked yet\n\nAdd a domain above — blocked sites\ncannot be removed until your commitment ends.",
                        14f, TEXT_GREY, gravity = Gravity.CENTER
                    ).apply {
                        setPadding(0, dp(32), 0, 0)
                        setLineSpacing(0f, 1.5f)
                        layoutParams = lp()
                    }
                )
                return@launch
            }

            val countLabel = text("${sites.size} site${if (sites.size == 1) "" else "s"} blocked", 11f, TEXT_GREY, letterSpacing = 0.1f)
            countLabel.setPadding(0, 0, 0, dp(10))
            blockedListContainer.addView(countLabel)

            sites.forEach { site -> blockedListContainer.addView(siteRow(site)) }
        }
    }

    // ── Row factory ────────────────────────────────────────────────────────────

    private fun siteRow(site: BlockedSite): View {
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
        textBlock.addView(statusPill("● Blocked in Chrome", RED, RED_DIM).apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(4)
        })
        textBlock.addView(text("🔒 Blocked until commitment ends", 11f, TEXT_GREY).apply {
            setPadding(0, dp(4), 0, 0)
        })

        // Lock icon — no interaction, matches the apps tab behaviour
        val lockIcon = TextView(requireContext()).apply {
            text = "🔒"
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(8) }
        }

        row.addView(textBlock)
        row.addView(lockIcon)
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