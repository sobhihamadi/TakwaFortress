package com.example.takwafortress.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.takwafortress.ui.activities.CommitmentSelectionActivity
import com.example.takwafortress.ui.activities.LoginActivity
import com.example.takwafortress.ui.viewmodels.FortressStatusViewModel
import android.app.AlertDialog
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

private val BG_DARK   = Color.parseColor("#1A1A2E")
private val CARD_BG   = Color.parseColor("#16213E")
private val GREEN     = Color.parseColor("#4CAF50")
private val RED       = Color.parseColor("#F44336")
private val ORANGE    = Color.parseColor("#FF9800")
private val TEXT_WHITE = Color.WHITE
private val TEXT_GREY = Color.parseColor("#9E9E9E")

class DashboardFragment : Fragment() {

    private lateinit var viewModel: FortressStatusViewModel
    private val auth = FirebaseAuth.getInstance()

    private var startInExpiredMode = false

    // ── View refs ──
    private lateinit var countdownText: TextView
    private lateinit var planNameText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var dnsStatusText: TextView
    private lateinit var browsersStatusText: TextView
    private lateinit var blockedAppsStatusText: TextView
    private lateinit var expiredBanner: TextView
    private lateinit var expiredActions: LinearLayout
    private lateinit var normalContent: LinearLayout

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
        // ✅ Get ViewModel scoped to the ACTIVITY — shared across all fragments
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
    }

    private fun buildUi(): LinearLayout {
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
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        // Expired banner
        expiredBanner = TextView(requireContext()).apply {
            text = "⏰ Your commitment period has ended.\nAll protections have been removed."
            textSize = 15f; setTextColor(ORANGE); gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.parseColor("#2A1500"))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        content.addView(expiredBanner)

        // Expired actions: Start New Plan + Logout
        expiredActions = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val newPlanBtn = makeButton("🔄 Start New Commitment Plan", GREEN)
        newPlanBtn.setOnClickListener { navigateToNewPlan() }
        val logoutBtn = makeButton("🚪 Logout", RED).apply {
            layoutParams = (layoutParams as LinearLayout.LayoutParams).apply { topMargin = dp(8) }
        }
        logoutBtn.setOnClickListener { showLogoutConfirmation() }
        expiredActions.addView(newPlanBtn); expiredActions.addView(logoutBtn)
        content.addView(expiredActions)

        // Normal content
        normalContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        planNameText = TextView(requireContext()).apply {
            text = "Loading..."; textSize = 20f; setTextColor(GREEN); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        normalContent.addView(planNameText)

        val countdownCard = makeCard()
        countdownCard.addView(makeLabel("⏱ TIME REMAINING", 13f, TEXT_GREY, Gravity.CENTER))
        countdownText = TextView(requireContext()).apply {
            text = "00 : 00 : 00 : 00"; textSize = 42f; setTextColor(TEXT_WHITE)
            gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(4))
        }
        countdownCard.addView(countdownText)
        countdownCard.addView(makeLabel("DAYS : HOURS : MINS : SECS", 11f, TEXT_GREY, Gravity.CENTER))
        normalContent.addView(countdownCard)

        val progressCard = makeCard()
        progressText = makeLabel("0% Complete", 15f, TEXT_WHITE)
        progressText.setPadding(0, 0, 0, dp(8))
        progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(GREEN)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12))
        }
        progressCard.addView(progressText); progressCard.addView(progressBar)
        normalContent.addView(progressCard)

        val protCard = makeCard()
        protCard.addView(makeLabel("🛡 PROTECTION STATUS", 15f, TEXT_WHITE).also { it.setPadding(0, 0, 0, dp(10)) })
        dnsStatusText         = makeLabel("✅ DNS Filter: Active", 14f, GREEN).also { it.setPadding(0, dp(4), 0, dp(4)) }
        browsersStatusText    = makeLabel("🌐 Browsers: 0/0 suspended", 14f, TEXT_WHITE).also { it.setPadding(0, dp(4), 0, dp(4)) }
        blockedAppsStatusText = makeLabel("🚫 Blocked Apps: 0", 14f, TEXT_WHITE).also { it.setPadding(0, dp(4), 0, dp(4)) }
        protCard.addView(dnsStatusText); protCard.addView(browsersStatusText); protCard.addView(blockedAppsStatusText)
        normalContent.addView(protCard)

        val refreshBtn = makeButton("🔄 Refresh Status", GREEN)
        refreshBtn.setOnClickListener { viewModel.loadAppList(); viewModel.loadCommitmentFromFirestore() }
        normalContent.addView(refreshBtn)

        content.addView(normalContent)
        scroll.addView(content)
        root.addView(scroll)
        return root
    }

    private fun observeViewModel() {
        viewModel.remainingTime.observe(viewLifecycleOwner)  { countdownText.text = it.formatted }
        viewModel.progressPercent.observe(viewLifecycleOwner) { pct ->
            progressBar.progress = pct; progressText.text = "$pct% Complete"
        }
        viewModel.planName.observe(viewLifecycleOwner) { planNameText.text = it }
        viewModel.protectionReport.observe(viewLifecycleOwner) { report ->
            dnsStatusText.text = if (report.isDnsActive) "✅ DNS Filter: Active" else "❌ DNS Filter: Inactive"
            dnsStatusText.setTextColor(if (report.isDnsActive) GREEN else RED)
            browsersStatusText.text = "🌐 Browsers: ${report.suspendedBrowsers}/${report.totalBrowsers} suspended"
            blockedAppsStatusText.text = "🚫 Blocked Apps: ${report.totalBlockedApps}"
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

    // ── UI helpers ──
    private fun makeCard() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(CARD_BG)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = dp(10) }
    }
    private fun makeLabel(text: String, size: Float, color: Int, gravity: Int = Gravity.START) =
        TextView(requireContext()).apply { this.text = text; textSize = size; setTextColor(color); this.gravity = gravity }
    private fun makeButton(label: String, color: Int) = Button(requireContext()).apply {
        text = label; textSize = 15f; setTextColor(TEXT_WHITE); setBackgroundColor(color)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}