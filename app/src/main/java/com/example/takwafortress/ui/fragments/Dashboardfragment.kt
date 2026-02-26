package com.example.takwafortress.ui.fragments

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.takwafortress.services.filtering.ContentFilteringService
import com.example.takwafortress.services.filtering.ContentFilterResult
import com.example.takwafortress.ui.activities.CommitmentSelectionActivity
import com.example.takwafortress.ui.activities.LoginActivity
import com.example.takwafortress.ui.viewmodels.FortressStatusViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ══════════════════════════════════════════════════════════════════════════════
// PALETTE — Soft Dark  (lifted, breathable, Notion/Linear feel)
// ══════════════════════════════════════════════════════════════════════════════
private val BG_DARK         = Color.parseColor("#161B27")   // background — warm navy, not black
private val CARD_BG         = Color.parseColor("#1E2535")   // card surface — clear lift above bg
private val CARD_BORDER     = Color.parseColor("#2A3347")   // border — visible but soft
private val HERO_BG         = Color.parseColor("#152238")   // hero card — deep blue-navy
private val HERO_BORDER     = Color.parseColor("#1F3554")   // hero card border

private val BLUE            = Color.parseColor("#4A90D9")   // primary accent — soft blue
private val BLUE_DIM        = Color.parseColor("#172540")   // blue tint bg (buttons)

private val GREEN           = Color.parseColor("#5DB88A")   // active — muted teal-green, not neon
private val GREEN_DIM       = Color.parseColor("#1A3040")   // green pill bg — navy-tinted

private val RED             = Color.parseColor("#E05C5C")   // error / inactive
private val RED_DIM         = Color.parseColor("#2E1A1A")   // red tint bg

private val ORANGE          = Color.parseColor("#D4924A")   // expired warning
private val ORANGE_DIM      = Color.parseColor("#2A1E0E")   // orange tint bg

private val TEXT_WHITE      = Color.parseColor("#EFF3F8")   // headings — soft white, not harsh
private val TEXT_SOFT       = Color.parseColor("#D4DCE8")   // body — warm, easy to read
private val TEXT_GREY       = Color.parseColor("#7A8BA0")   // captions — bluer, matches palette
private val DIVIDER         = Color.parseColor("#252E3F")   // subtle divider

class DashboardFragment : Fragment() {

    private lateinit var viewModel: FortressStatusViewModel
    private lateinit var contentFilteringService: ContentFilteringService
    private val auth = FirebaseAuth.getInstance()

    private var startInExpiredMode = false

    private lateinit var countdownText: TextView
    private lateinit var planNameText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var dnsStatusDot: TextView
    private lateinit var dnsLayerText: TextView
    private lateinit var dnsStatusPill: LinearLayout
    private lateinit var blockedAppsStatusText: TextView
    private lateinit var expiredBanner: TextView
    private lateinit var expiredActions: LinearLayout
    private lateinit var normalContent: LinearLayout
    private lateinit var dnsActivateBtn: Button
    private lateinit var dnsTestBtn: Button
    private lateinit var dnsProgressBar: ProgressBar
    private var progressPctLabel: TextView? = null

    companion object {
        private const val ARG_EXPIRED = "arg_expired"
        fun newInstance(startExpired: Boolean = false) = DashboardFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_EXPIRED, startExpired) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startInExpiredMode = arguments?.getBoolean(ARG_EXPIRED, false) ?: false
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        contentFilteringService = ContentFilteringService(requireContext())
        viewModel = ViewModelProvider(requireActivity(), object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return FortressStatusViewModel(requireContext()) as T
            }
        })[FortressStatusViewModel::class.java]
        return buildUi()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        if (startInExpiredMode) applyExpiredMode(true)
        refreshDnsStatus()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BUILD UI
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildUi(): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG_DARK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val scroll = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val sb = resources.getIdentifier("status_bar_height", "dimen", "android")
                .let { if (it > 0) resources.getDimensionPixelSize(it) else dp(24) }
            setPadding(dp(16), sb + dp(12), dp(16), dp(32))
        }

        // ── Expired banner ────────────────────────────────────────────────────
        expiredBanner = TextView(requireContext()).apply {
            text = "⏰  Your commitment period has ended.\nAll protections have been removed."
            textSize = 14f; setTextColor(ORANGE); gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(ORANGE_DIM)
                setStroke(dp(1), Color.parseColor("#3A2A10"))
                cornerRadius = dp(10).toFloat()
            }
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }
        content.addView(expiredBanner)

        // ── Expired actions ───────────────────────────────────────────────────
        expiredActions = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        expiredActions.addView(makePrimaryBtn("🔄  Start New Commitment", Color.WHITE, BLUE).also {
            it.setOnClickListener { navigateToNewPlan() }
        })
        expiredActions.addView(makeSecondaryBtn("🚪  Logout", RED, RED_DIM).also {
            it.layoutParams = (it.layoutParams as LinearLayout.LayoutParams).apply { topMargin = dp(8) }
            it.setOnClickListener { showLogoutConfirmation() }
        })
        content.addView(expiredActions)

        // ── Normal content ────────────────────────────────────────────────────
        normalContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // ── App identity header ───────────────────────────────────────────────
        normalContent.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(20) }

            addView(TextView(requireContext()).apply {
                text = "TAKWA FORTRESS"
                textSize = 10f; setTextColor(TEXT_GREY); letterSpacing = 0.25f; gravity = Gravity.CENTER
            })
            planNameText = TextView(requireContext()).apply {
                text = "Loading..."
                textSize = 20f; setTextColor(TEXT_WHITE); typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER; letterSpacing = 0.01f
                setPadding(0, dp(4), 0, 0)
            }
            addView(planNameText)
        })

        // ── HERO countdown card ───────────────────────────────────────────────
        val heroCard = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            background = cardDrawable(HERO_BG, HERO_BORDER)
        }

        val heroHeader = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }
        heroHeader.addView(View(requireContext()).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(BLUE)
            }
            layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                rightMargin = dp(8); gravity = Gravity.CENTER_VERTICAL
            }
        })
        heroHeader.addView(TextView(requireContext()).apply {
            text = "TIME REMAINING"
            textSize = 10f; setTextColor(Color.parseColor("#7FA8CC")); letterSpacing = 0.2f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        heroCard.addView(heroHeader)

        countdownText = TextView(requireContext()).apply {
            text = "-- : -- : -- : --"
            textSize = 40f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE; letterSpacing = 0.05f
            setPadding(0, dp(4), 0, dp(8))
        }
        heroCard.addView(countdownText)

        heroCard.addView(TextView(requireContext()).apply {
            text = "DAYS          HRS          MIN          SEC"
            textSize = 9f; setTextColor(Color.parseColor("#5A7A9A"))
            gravity = Gravity.CENTER; letterSpacing = 0.15f
        })
        normalContent.addView(heroCard)

        // ── Progress card ─────────────────────────────────────────────────────
        val progressCard = makeCard()
        val progressHeaderRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        progressText = TextView(requireContext()).apply {
            text = "Commitment Progress"
            textSize = 13f; setTextColor(TEXT_SOFT)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        progressPctLabel = TextView(requireContext()).apply {
            text = "0%"; textSize = 13f; setTextColor(BLUE); typeface = Typeface.DEFAULT_BOLD
        }
        progressHeaderRow.addView(progressText)
        progressHeaderRow.addView(progressPctLabel)
        progressCard.addView(progressHeaderRow)

        progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(BLUE)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#252E3F"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6))
        }
        progressCard.addView(progressBar)
        normalContent.addView(progressCard)

        // ── Protection status card ────────────────────────────────────────────
        val protCard = makeCard()
        protCard.addView(makeSectionLabel("🛡️  PROTECTION STATUS"))

        val dnsRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14); bottomMargin = dp(12) }
        }
        val dnsLabelBlock = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        dnsLabelBlock.addView(TextView(requireContext()).apply {
            text = "DNS Filter"; textSize = 14f; setTextColor(TEXT_WHITE); typeface = Typeface.DEFAULT_BOLD
        })
        dnsLabelBlock.addView(TextView(requireContext()).apply {
            text = "Content blocking layer"; textSize = 11f; setTextColor(TEXT_GREY); setPadding(0, dp(2), 0, 0)
        })

        dnsStatusPill = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(GREEN_DIM); cornerRadius = dp(20).toFloat()
            }
        }
        dnsStatusDot = TextView(requireContext()).apply {
            text = "●"; textSize = 8f; setTextColor(GREEN); setPadding(0, 0, dp(4), 0)
        }
        dnsLayerText = TextView(requireContext()).apply {
            text = "Active"; textSize = 11f; setTextColor(GREEN); typeface = Typeface.DEFAULT_BOLD
        }
        dnsStatusPill.addView(dnsStatusDot); dnsStatusPill.addView(dnsLayerText)

        dnsRow.addView(dnsLabelBlock); dnsRow.addView(dnsStatusPill)
        protCard.addView(dnsRow)

        protCard.addView(View(requireContext()).apply {
            setBackgroundColor(DIVIDER)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { bottomMargin = dp(10) }
        })

        blockedAppsStatusText = makeStatusRow("🚫  Blocked Apps", "0 apps", protCard)
        normalContent.addView(protCard)

        // ── DNS Controls card ─────────────────────────────────────────────────
        val dnsCard = makeCard()
        dnsCard.addView(makeSectionLabel("🌐  CONTENT FILTERING"))

        dnsProgressBar = ProgressBar(requireContext()).apply {
            visibility = View.GONE
            indeterminateTintList = android.content.res.ColorStateList.valueOf(BLUE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER; topMargin = dp(8) }
        }

        dnsActivateBtn = makePrimaryBtn("🚀  Activate Full Protection", Color.WHITE, BLUE).apply {
            layoutParams = (layoutParams as LinearLayout.LayoutParams).apply {
                topMargin = dp(12); bottomMargin = dp(8)
            }
        }
        dnsActivateBtn.setOnClickListener {
            dnsProgressBar.visibility = View.VISIBLE; dnsActivateBtn.isEnabled = false
            lifecycleScope.launch {
                val result = contentFilteringService.activateFullProtection()
                dnsProgressBar.visibility = View.GONE; dnsActivateBtn.isEnabled = true
                when (result) {
                    is ContentFilterResult.Success -> {
                        Toast.makeText(requireContext(), "✅ Protection activated!", Toast.LENGTH_SHORT).show()
                        refreshDnsStatus()
                    }
                    is ContentFilterResult.Failed ->
                        Toast.makeText(requireContext(), "❌ ${result.reason}", Toast.LENGTH_LONG).show()
                    else ->
                        Toast.makeText(requireContext(), "❌ Device Owner required", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dnsTestBtn = makeSecondaryBtn("🧪  Test DNS Filtering", BLUE, BLUE_DIM)
        dnsTestBtn.setOnClickListener {
            dnsProgressBar.visibility = View.VISIBLE; dnsTestBtn.isEnabled = false
            lifecycleScope.launch {
                val blocked = isDnsBlocking("pornhub.com")
                dnsProgressBar.visibility = View.GONE; dnsTestBtn.isEnabled = true
                Toast.makeText(
                    requireContext(),
                    if (blocked) "✅ DNS is blocking adult content correctly."
                    else "⚠️ DNS may not be active. Tap Activate to fix.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        dnsCard.addView(dnsActivateBtn); dnsCard.addView(dnsTestBtn); dnsCard.addView(dnsProgressBar)
        normalContent.addView(dnsCard)

        // ── Refresh — ghost, lowest visual priority ───────────────────────────
        normalContent.addView(makeGhostBtn("🔄  Refresh Status").apply {
            setOnClickListener {
                viewModel.loadAppList(); viewModel.loadCommitmentFromFirestore(); refreshDnsStatus()
            }
        })

        content.addView(normalContent)
        scroll.addView(content)
        root.addView(scroll)
        return root
    }

    // ══════════════════════════════════════════════════════════════════════════
    // OBSERVE
    // ══════════════════════════════════════════════════════════════════════════

    private fun observeViewModel() {
        viewModel.remainingTime.observe(viewLifecycleOwner) { countdownText.text = it.formatted }
        viewModel.progressPercent.observe(viewLifecycleOwner) { pct ->
            progressBar.progress = pct
            progressPctLabel?.text = "$pct%"
        }
        viewModel.planName.observe(viewLifecycleOwner) { planNameText.text = it }
        viewModel.protectionReport.observe(viewLifecycleOwner) { report ->
            updateDnsPill(report.isDnsActive)
            blockedAppsStatusText.text = "${report.totalBlockedApps} apps"
        }
        viewModel.isCommitmentExpired.observe(viewLifecycleOwner) { expired ->
            if (!startInExpiredMode) applyExpiredMode(expired)
        }
    }

    fun applyExpiredMode(expired: Boolean) {
        expiredBanner.visibility  = if (expired) View.VISIBLE else View.GONE
        expiredActions.visibility = if (expired) View.VISIBLE else View.GONE
        normalContent.visibility  = if (expired) View.GONE   else View.VISIBLE
    }

    private fun refreshDnsStatus() {
        val s = contentFilteringService.getProtectionStatus()
        updateDnsPill(s.dnsFilterActive)
    }

    private fun updateDnsPill(active: Boolean) {
        val pillBg = dnsStatusPill.background as? GradientDrawable
        pillBg?.setColor(if (active) GREEN_DIM else RED_DIM)
        dnsStatusDot.setTextColor(if (active) GREEN else RED)
        dnsLayerText.text = if (active) "Active" else "Inactive"
        dnsLayerText.setTextColor(if (active) GREEN else RED)
    }

    private suspend fun isDnsBlocking(domain: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val ip = java.net.InetAddress.getByName(domain).hostAddress ?: return@withContext false
            ip == "0.0.0.0" || ip.startsWith("185.228")
        } catch (e: java.net.UnknownHostException) { true }
        catch (e: Exception) { false }
    }

    private fun navigateToNewPlan() {
        startActivity(Intent(requireContext(), CommitmentSelectionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        requireActivity().finish()
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                auth.signOut()
                startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                requireActivity().finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UI FACTORIES
    // ══════════════════════════════════════════════════════════════════════════

    private fun makeCard() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }
        background = cardDrawable(CARD_BG, CARD_BORDER)
    }

    private fun cardDrawable(fill: Int, border: Int): LayerDrawable {
        val base = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            setStroke(dp(1), border)
            cornerRadius = dp(12).toFloat()
        }
        val topHighlight = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#354059"))   // soft blue-grey highlight line
            cornerRadius = dp(12).toFloat()
        }
        return LayerDrawable(arrayOf(base, topHighlight)).apply {
            setLayerInset(1, dp(1), dp(1), dp(1), 0)
            setLayerHeight(1, dp(1))
            setLayerGravity(1, Gravity.TOP)
        }
    }

    private fun makeStatusRow(label: String, initialValue: String, parent: LinearLayout): TextView {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        row.addView(TextView(requireContext()).apply {
            text = label; textSize = 13f; setTextColor(TEXT_GREY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val value = TextView(requireContext()).apply {
            text = initialValue; textSize = 13f
            setTextColor(TEXT_SOFT); typeface = Typeface.DEFAULT_BOLD
        }
        row.addView(value); parent.addView(row)
        return value
    }

    private fun makeSectionLabel(text: String) = TextView(requireContext()).apply {
        this.text = text; textSize = 11f; setTextColor(TEXT_GREY); letterSpacing = 0.12f
    }

    /** Primary button — solid blue fill, white text */
    private fun makePrimaryBtn(label: String, textColor: Int, bgColor: Int) =
        Button(requireContext()).apply {
            text = label; textSize = 14f; setTextColor(textColor); typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(bgColor)
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(16), 0, dp(16), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
        }

    /** Secondary button — light tint bg, colored text, soft border */
    private fun makeSecondaryBtn(label: String, textColor: Int, bgColor: Int) =
        Button(requireContext()).apply {
            text = label; textSize = 14f; setTextColor(textColor)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(bgColor)
                setStroke(dp(1), textColor.withAlpha(40))
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(16), 0, dp(16), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
        }

    /** Ghost button — transparent, barely visible border */
    private fun makeGhostBtn(label: String) = Button(requireContext()).apply {
        text = label; textSize = 12f; setTextColor(TEXT_GREY)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.TRANSPARENT)
            setStroke(dp(1), CARD_BORDER)
        }
        setPadding(dp(16), 0, dp(16), 0)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
        ).apply { topMargin = dp(4) }
    }

    private fun Int.withAlpha(alpha: Int) =
        Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}