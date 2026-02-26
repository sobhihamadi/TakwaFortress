package com.example.takwafortress.ui.fragments

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.Context
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.takwafortress.model.builders.BlockedAppBuilder
import com.example.takwafortress.model.builders.IdentifierBlockedAppBuilder
import com.example.takwafortress.repository.implementations.LocalBlockedAppRepository
import com.example.takwafortress.services.verification.AppSearchResult
import com.example.takwafortress.services.verification.GooglePlayVerificationService
import com.example.takwafortress.ui.viewmodels.FortressStatusViewModel
import kotlinx.coroutines.launch
import java.util.UUID

// ══════════════════════════════════════════════════════════════════════════════
// PALETTE — Soft Dark  (matches DashboardFragment — lifted, breathable)
// ══════════════════════════════════════════════════════════════════════════════
private val BG_DARK      = Color.parseColor("#161B27")   // background — warm navy, not black
private val CARD_BG      = Color.parseColor("#1E2535")   // card surface — clear lift above bg
private val INPUT_BG     = Color.parseColor("#141929")   // input field — slightly deeper than card
private val BORDER_DIM   = Color.parseColor("#2A3347")   // border — visible but soft
private val BORDER_TOP   = Color.parseColor("#354059")   // top-highlight line — blue-grey
private val GREEN        = Color.parseColor("#5DB88A")   // active — muted teal-green, not neon
private val GREEN_BRIGHT = Color.parseColor("#5DB88A")   // same — keeps one consistent green
private val GREEN_DIM    = Color.parseColor("#1A3040")   // green pill bg — navy-tinted
private val RED          = Color.parseColor("#E05C5C")   // error / block
private val RED_DIM      = Color.parseColor("#2E1A1A")   // red tint bg
private val ORANGE       = Color.parseColor("#D4924A")   // warning / not installed
private val TEXT_WHITE   = Color.parseColor("#EFF3F8")   // headings — soft white, not harsh
private val TEXT_SOFT    = Color.parseColor("#D4DCE8")   // body — warm, easy to read
private val TEXT_GREY    = Color.parseColor("#7A8BA0")   // captions — bluer, matches palette
private val DIVIDER_CLR  = Color.parseColor("#252E3F")   // subtle divider

class AppsFragment : Fragment() {

    private lateinit var viewModel: FortressStatusViewModel
    private lateinit var repository: LocalBlockedAppRepository
    private lateinit var verificationService: GooglePlayVerificationService
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: android.content.ComponentName

    private var isExpired = false

    // UI refs
    private lateinit var searchInput: EditText
    private lateinit var searchButton: Button
    private lateinit var searchProgressBar: ProgressBar
    private lateinit var searchStatusText: TextView
    private lateinit var suggestionsContainer: LinearLayout
    private lateinit var installedAppsContainer: LinearLayout
    private lateinit var blockedAppsContainer: LinearLayout
    private lateinit var tabInstalledApps: LinearLayout
    private lateinit var tabInstalledLabel: TextView
    private lateinit var tabBlockedList: LinearLayout
    private lateinit var tabBlockedLabel: TextView
    private lateinit var contentRoot: LinearLayout

    companion object {
        private const val ARG_EXPIRED = "arg_expired"
        private const val TAG = "AppsFragment"
        fun newInstance(startExpired: Boolean = false) = AppsFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_EXPIRED, startExpired) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isExpired = arguments?.getBoolean(ARG_EXPIRED, false) ?: false
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        repository          = LocalBlockedAppRepository(requireContext())
        verificationService = GooglePlayVerificationService(requireContext())
        devicePolicyManager = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent      = android.content.ComponentName(
            requireContext(),
            com.example.takwafortress.receivers.DeviceAdminReceiver::class.java
        )
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
        viewModel.isCommitmentExpired.observe(viewLifecycleOwner) { applyExpiredMode(it) }
    }

    override fun onResume() {
        super.onResume()
        if (!isExpired) loadBlockedAppsData()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BUILD UI
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(BG_DARK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }

        contentRoot = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), getStatusBarHeight() + dp(12), dp(16), dp(32))
        }

        // ── Header ────────────────────────────────────────────────────────────
        contentRoot.addView(TextView(requireContext()).apply {
            text = "TAKWA FORTRESS"
            textSize = 10f; setTextColor(TEXT_GREY); letterSpacing = 0.25f; gravity = Gravity.CENTER
        })
        contentRoot.addView(TextView(requireContext()).apply {
            text = "App Blocking"
            textSize = 20f; setTextColor(TEXT_WHITE); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; letterSpacing = 0.03f; setPadding(0, dp(4), 0, dp(6))
        })
        contentRoot.addView(TextView(requireContext()).apply {
            text = "Manage apps blocked during your commitment"
            textSize = 13f; setTextColor(TEXT_GREY); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        })

        // ── Search card ───────────────────────────────────────────────────────
        val searchCard = makeCard()

        searchCard.addView(TextView(requireContext()).apply {
            text = "🔍  ADD APP TO BLOCK LIST"
            textSize = 11f; setTextColor(TEXT_GREY); letterSpacing = 0.12f
            setPadding(0, 0, 0, dp(14))
        })

        searchInput = EditText(requireContext()).apply {
            hint = "Search by name  (e.g. Instagram, TikTok)"
            setTextColor(TEXT_WHITE); setHintTextColor(TEXT_GREY)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(INPUT_BG)
                setStroke(dp(1), BORDER_DIM)
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)
            ).apply { bottomMargin = dp(10) }
        }
        searchCard.addView(searchInput)

        suggestionsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
        }
        searchCard.addView(suggestionsContainer)

        // Primary search button — matches Dashboard primary btn style
        searchButton = Button(requireContext()).apply {
            text = "Search & Verify"
            textSize = 14f; setTextColor(TEXT_WHITE); typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#4A90D9"))   // same BLUE as Dashboard primary
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
        }
        searchCard.addView(searchButton)

        searchProgressBar = ProgressBar(requireContext()).apply {
            visibility = View.GONE
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4A90D9"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER; topMargin = dp(10) }
        }
        searchCard.addView(searchProgressBar)

        searchStatusText = TextView(requireContext()).apply {
            textSize = 13f; setTextColor(TEXT_GREY); visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }
        searchCard.addView(searchStatusText)
        contentRoot.addView(searchCard)

        // ── Tab bar ───────────────────────────────────────────────────────────
        val tabBarContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(INPUT_BG)
                setStroke(dp(1), BORDER_DIM)
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
            ).apply { topMargin = dp(6); bottomMargin = dp(14) }
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        tabInstalledApps = makeTabPill("📱  Installed Apps", active = true)
        tabBlockedList   = makeTabPill("🚫  Blocked List (0)", active = false)

        tabBarContainer.addView(tabInstalledApps)
        tabBarContainer.addView(tabBlockedList)
        contentRoot.addView(tabBarContainer)

        tabInstalledLabel = tabInstalledApps.getChildAt(0) as TextView
        tabBlockedLabel   = tabBlockedList.getChildAt(0) as TextView

        // ── List containers ───────────────────────────────────────────────────
        installedAppsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        blockedAppsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        contentRoot.addView(installedAppsContainer)
        contentRoot.addView(blockedAppsContainer)

        // ── Listeners ─────────────────────────────────────────────────────────
        tabInstalledApps.setOnClickListener { if (!isExpired) switchSubTab(0) else showExpiredToast() }
        tabBlockedList.setOnClickListener   { if (!isExpired) switchSubTab(1) else showExpiredToast() }
        searchButton.setOnClickListener     { if (!isExpired) performSearch()  else showExpiredToast() }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                suggestionsContainer.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        scroll.addView(contentRoot)
        return scroll
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EXPIRED MODE
    // ══════════════════════════════════════════════════════════════════════════

    fun applyExpiredMode(expired: Boolean) {
        isExpired = expired
        contentRoot.alpha = if (expired) 0.35f else 1.0f
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TAB SWITCHING
    // ══════════════════════════════════════════════════════════════════════════

    private fun switchSubTab(tab: Int) {
        if (tab == 0) {
            setTabActive(tabInstalledApps, tabInstalledLabel)
            setTabInactive(tabBlockedList, tabBlockedLabel)
            installedAppsContainer.visibility = View.VISIBLE
            blockedAppsContainer.visibility   = View.GONE
        } else {
            setTabActive(tabBlockedList, tabBlockedLabel)
            setTabInactive(tabInstalledApps, tabInstalledLabel)
            installedAppsContainer.visibility = View.GONE
            blockedAppsContainer.visibility   = View.VISIBLE
        }
    }

    private fun setTabActive(pill: LinearLayout, label: TextView) {
        pill.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(GREEN_DIM)
            setStroke(dp(1), GREEN_BRIGHT.withAlpha(55))
            cornerRadius = dp(8).toFloat()
        }
        label.setTextColor(GREEN_BRIGHT)
        label.typeface = Typeface.DEFAULT_BOLD
    }

    private fun setTabInactive(pill: LinearLayout, label: TextView) {
        pill.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(8).toFloat()
        }
        label.setTextColor(TEXT_GREY)
        label.typeface = Typeface.DEFAULT
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SEARCH
    // ══════════════════════════════════════════════════════════════════════════

    private fun performSearch() {
        val query = searchInput.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter an app name", Toast.LENGTH_SHORT).show()
            return
        }
        searchProgressBar.visibility = View.VISIBLE
        searchStatusText.visibility  = View.GONE
        suggestionsContainer.removeAllViews()
        suggestionsContainer.visibility = View.GONE

        lifecycleScope.launch {
            val results = verificationService.searchPlayStore(query)
            searchProgressBar.visibility = View.GONE
            if (results.isEmpty()) {
                searchStatusText.text = "⚠️  No apps found. Check spelling or try the package name."
                searchStatusText.setTextColor(ORANGE)
                searchStatusText.visibility = View.VISIBLE
            } else {
                displaySearchResults(results)
            }
        }
    }

    private fun displaySearchResults(results: List<AppSearchResult>) {
        suggestionsContainer.removeAllViews()
        suggestionsContainer.visibility = View.VISIBLE

        suggestionsContainer.addView(TextView(requireContext()).apply {
            text = "${results.size} result${if (results.size == 1) "" else "s"} found"
            textSize = 11f; setTextColor(TEXT_GREY); letterSpacing = 0.1f
            setPadding(0, dp(10), 0, dp(10))
        })

        results.take(5).forEach { result ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(INPUT_BG); setStroke(dp(1), BORDER_DIM); cornerRadius = dp(10).toFloat()
                }
            }

            val textBlock = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textBlock.addView(TextView(requireContext()).apply {
                text = result.appName; textSize = 15f; setTextColor(TEXT_WHITE); typeface = Typeface.DEFAULT_BOLD
            })
            textBlock.addView(TextView(requireContext()).apply {
                text = result.packageName; textSize = 11f; setTextColor(TEXT_GREY); setPadding(0, dp(2), 0, dp(4))
            })
            textBlock.addView(makeStatusPill(
                if (result.isInstalled) "● Installed" else "○ Not installed",
                if (result.isInstalled) GREEN_BRIGHT else ORANGE,
                if (result.isInstalled) GREEN_DIM else Color.parseColor("#2A1E0E")
            ))

            val blockBtn = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                setPadding(dp(10), dp(5), dp(12), dp(5))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(RED_DIM); setStroke(dp(1), RED.withAlpha(60)); cornerRadius = dp(20).toFloat()
                }
            }
            blockBtn.addView(TextView(requireContext()).apply {
                text = "Block"; textSize = 12f; setTextColor(RED); typeface = Typeface.DEFAULT_BOLD
            })
            blockBtn.setOnClickListener { confirmBlockApp(result) }

            row.addView(textBlock); row.addView(blockBtn)
            suggestionsContainer.addView(row)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BLOCK ACTIONS
    // ══════════════════════════════════════════════════════════════════════════

    private fun confirmBlockApp(app: AppSearchResult) {
        AlertDialog.Builder(requireContext())
            .setTitle("Block ${app.appName}?")
            .setMessage("This app will be blocked until your commitment ends. You cannot undo this.")
            .setPositiveButton("Block It") { _, _ -> addToBlockList(app) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addToBlockList(app: AppSearchResult) {
        lifecycleScope.launch {
            try {
                val blockedApp = IdentifierBlockedAppBuilder.newBuilder()
                    .setId(UUID.randomUUID().toString())
                    .setBlockedApp(
                        BlockedAppBuilder.newBuilder()
                            .setPackageName(app.packageName)
                            .setAppName(app.appName)
                            .setIsSystemApp(false)
                            .setIsSuspended(true)
                            .setBlockReason(if (app.isInstalled) "User blocked" else "Pre-blocked")
                            .setDetectedDate(System.currentTimeMillis())
                            .setIsInstalled(app.isInstalled)
                            .setIsPreBlocked(!app.isInstalled)
                            .build()
                    ).build()

                repository.create(blockedApp)
                if (app.isInstalled) blockAppNow(app.packageName)

                Toast.makeText(requireContext(), "✅  ${app.appName} blocked", Toast.LENGTH_SHORT).show()
                searchInput.text.clear()
                suggestionsContainer.removeAllViews()
                suggestionsContainer.visibility = View.GONE
                loadBlockedAppsData()
            } catch (e: Exception) {
                Log.e(TAG, "Error blocking app", e)
            }
        }
    }

    private fun blockAppNow(packageName: String) {
        try {
            val isNuclear = com.example.takwafortress.model.entities.BlockedApp.isNuclearApp(packageName)
            if (isNuclear) devicePolicyManager.setApplicationHidden(adminComponent, packageName, true)
            else           devicePolicyManager.setPackagesSuspended(adminComponent, arrayOf(packageName), true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to block $packageName", e)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOAD DATA
    // ══════════════════════════════════════════════════════════════════════════

    private fun loadBlockedAppsData() {
        lifecycleScope.launch { loadInstalledApps(); loadBlockedList() }
    }

    private suspend fun loadInstalledApps() {
        installedAppsContainer.removeAllViews()
        val allApps     = verificationService.getAllInstallableApps()
        val blockedPkgs = repository.getAll().map { it.getPackageName() }.toSet()
        val available   = allApps.filter { it.packageName !in blockedPkgs }

        if (available.isEmpty()) {
            installedAppsContainer.addView(makeEmptyState("✅  All apps are managed", GREEN_BRIGHT))
        } else {
            installedAppsContainer.addView(makeSectionCountLabel("${available.size} apps available"))
            available.forEach { app ->
                installedAppsContainer.addView(makeAppRow(
                    name       = app.appName,
                    pkg        = app.packageName,
                    pillText   = "● Installed",
                    pillColor  = GREEN_BRIGHT,
                    pillBg     = GREEN_DIM,
                    actionView = makeBlockPill("Block") { confirmBlockApp(app) }
                ))
            }
        }
    }

    private suspend fun loadBlockedList() {
        blockedAppsContainer.removeAllViews()
        val blocked = repository.getAll()

        tabBlockedLabel.text = "🚫  Blocked (${blocked.size})"

        if (blocked.isEmpty()) {
            blockedAppsContainer.addView(makeEmptyState("No apps blocked yet", TEXT_GREY))
        } else {
            blockedAppsContainer.addView(makeSectionCountLabel("${blocked.size} app${if (blocked.size == 1) "" else "s"} blocked"))
            blocked.forEach { app ->
                val isInstalled = app.getIsInstalled()
                blockedAppsContainer.addView(makeAppRow(
                    name       = app.getAppName(),
                    pkg        = app.getPackageName(),
                    pillText   = if (isInstalled) "● Blocked" else "○ Will block on install",
                    pillColor  = if (isInstalled) RED else ORANGE,
                    pillBg     = if (isInstalled) RED_DIM else Color.parseColor("#2A1E0E"),
                    actionView = makeLockIcon()
                ))
            }
        }
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
        background = cardDrawable(CARD_BG, BORDER_DIM)
    }

    private fun makeTabPill(label: String, active: Boolean) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        background = if (active) GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(GREEN_DIM); setStroke(dp(1), GREEN_BRIGHT.withAlpha(55)); cornerRadius = dp(8).toFloat()
        } else GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; setColor(Color.TRANSPARENT); cornerRadius = dp(8).toFloat()
        }
        addView(TextView(requireContext()).apply {
            text = label; textSize = 13f; gravity = Gravity.CENTER
            setTextColor(if (active) GREEN_BRIGHT else TEXT_GREY)
            typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        })
    }

    private fun makeAppRow(
        name: String,
        pkg: String,
        pillText: String,
        pillColor: Int,
        pillBg: Int,
        actionView: View
    ) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) }
        background = cardDrawable(CARD_BG, BORDER_DIM)

        val textBlock = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textBlock.addView(TextView(requireContext()).apply {
            text = name; textSize = 15f; setTextColor(TEXT_WHITE); typeface = Typeface.DEFAULT_BOLD
        })
        textBlock.addView(TextView(requireContext()).apply {
            text = pkg; textSize = 11f; setTextColor(TEXT_GREY); setPadding(0, dp(2), 0, dp(6))
        })
        textBlock.addView(makeStatusPill(pillText, pillColor, pillBg))

        addView(textBlock)
        addView(actionView)
    }

    private fun makeStatusPill(text: String, textColor: Int, bgColor: Int) =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(3), dp(10), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(bgColor); cornerRadius = dp(20).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(TextView(requireContext()).apply {
                this.text = text; textSize = 10f; setTextColor(textColor); typeface = Typeface.DEFAULT_BOLD
            })
        }

    private fun makeBlockPill(label: String, onClick: () -> Unit) =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(12), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(RED_DIM); setStroke(dp(1), RED.withAlpha(60)); cornerRadius = dp(20).toFloat()
            }
            addView(TextView(requireContext()).apply {
                text = label; textSize = 12f; setTextColor(RED); typeface = Typeface.DEFAULT_BOLD
            })
            setOnClickListener { onClick() }
        }

    private fun makeLockIcon() = TextView(requireContext()).apply {
        text = "🔒"; textSize = 18f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(8) }
    }

    private fun makeSectionCountLabel(text: String) = TextView(requireContext()).apply {
        this.text = text; textSize = 11f; setTextColor(TEXT_GREY); letterSpacing = 0.1f
        setPadding(0, 0, 0, dp(10))
    }

    private fun makeEmptyState(text: String, color: Int) = TextView(requireContext()).apply {
        this.text = text; textSize = 14f; setTextColor(color)
        gravity = Gravity.CENTER; setPadding(0, dp(40), 0, dp(16))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun cardDrawable(fill: Int, border: Int): LayerDrawable {
        val base = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill); setStroke(dp(1), border); cornerRadius = dp(12).toFloat()
        }
        val topHighlight = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; setColor(BORDER_TOP); cornerRadius = dp(12).toFloat()
        }
        return LayerDrawable(arrayOf(base, topHighlight)).apply {
            setLayerInset(1, dp(1), dp(1), dp(1), 0)
            setLayerHeight(1, dp(1))
            setLayerGravity(1, Gravity.TOP)
        }
    }

    private fun Int.withAlpha(alpha: Int) =
        Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))

    private fun showExpiredToast() {
        Toast.makeText(requireContext(), "Commitment ended — feature disabled", Toast.LENGTH_SHORT).show()
    }

    private fun getStatusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}