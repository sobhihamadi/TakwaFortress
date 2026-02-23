package com.example.takwafortress.ui.fragments

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.graphics.Color
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
import com.example.takwafortress.model.builders.BlockedAppBuilder
import com.example.takwafortress.model.builders.IdentifierBlockedAppBuilder
import com.example.takwafortress.repository.implementations.LocalBlockedAppRepository
import com.example.takwafortress.services.verification.AppSearchResult
import com.example.takwafortress.services.verification.GooglePlayVerificationService
import com.example.takwafortress.ui.viewmodels.FortressStatusViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.launch
import java.util.UUID

private val BG_DARK    = Color.parseColor("#1A1A2E")
private val CARD_BG    = Color.parseColor("#16213E")
private val GREEN      = Color.parseColor("#4CAF50")
private val RED        = Color.parseColor("#F44336")
private val ORANGE     = Color.parseColor("#FF9800")
private val TEXT_WHITE = Color.WHITE
private val TEXT_GREY  = Color.parseColor("#9E9E9E")

class AppsFragment : Fragment() {

    private lateinit var viewModel: FortressStatusViewModel
    private lateinit var repository: LocalBlockedAppRepository
    private lateinit var verificationService: GooglePlayVerificationService
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: android.content.ComponentName

    private var isExpired = false

    private lateinit var searchInput: EditText
    private lateinit var searchButton: Button
    private lateinit var searchProgressBar: ProgressBar
    private lateinit var searchStatusText: TextView
    private lateinit var suggestionsContainer: LinearLayout
    private lateinit var installedAppsContainer: LinearLayout
    private lateinit var blockedAppsContainer: LinearLayout
    private lateinit var tabInstalledApps: Button
    private lateinit var tabBlockedList: Button

    // Root content view — used to apply alpha dim when expired (same as DnsFragment)
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
        repository           = LocalBlockedAppRepository(requireContext())
        verificationService  = GooglePlayVerificationService(requireContext())
        devicePolicyManager  = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent       = android.content.ComponentName(
            requireContext(), com.example.takwafortress.receivers.DeviceAdminReceiver::class.java
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

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(BG_DARK)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // contentRoot is the reference we dim — same pattern as DnsFragment's normalContent
        contentRoot = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        // Search card
        val searchCard = makeCard()
        searchCard.addView(makeLabel("➕ Add App to Block List", 18f, TEXT_WHITE))
        searchCard.addView(makeLabel("Search by name or enter package name", 12f, TEXT_GREY).apply { setPadding(0, dp(4), 0, dp(12)) })

        searchInput = EditText(requireContext()).apply {
            hint = "Enter app name (e.g., Twitter, Instagram)"
            setTextColor(TEXT_WHITE); setHintTextColor(TEXT_GREY)
            setBackgroundColor(Color.parseColor("#0D0D1A"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(12) }
        }
        searchCard.addView(searchInput)

        suggestionsContainer = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        searchCard.addView(suggestionsContainer)

        searchButton = Button(requireContext()).apply {
            text = "🔍 Search & Verify"; textSize = 15f; setTextColor(TEXT_WHITE); setBackgroundColor(GREEN)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50))
        }
        searchCard.addView(searchButton)

        searchProgressBar = ProgressBar(requireContext()).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER; topMargin = dp(12) }
        }
        searchCard.addView(searchProgressBar)

        searchStatusText = makeLabel("", 14f, TEXT_GREY).apply { visibility = View.GONE; setPadding(0, dp(8), 0, 0) }
        searchCard.addView(searchStatusText)
        contentRoot.addView(searchCard)

        // Sub-tab bar
        val subTabBar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(16) }
        }
        tabInstalledApps = Button(requireContext()).apply {
            text = "📱 Installed Apps"; setBackgroundColor(GREEN); setTextColor(TEXT_WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        tabBlockedList = Button(requireContext()).apply {
            text = "🚫 Blocked List (0)"; setBackgroundColor(Color.parseColor("#2A2A3E")); setTextColor(TEXT_GREY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        subTabBar.addView(tabInstalledApps); subTabBar.addView(tabBlockedList)
        contentRoot.addView(subTabBar)

        installedAppsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        blockedAppsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        contentRoot.addView(installedAppsContainer)
        contentRoot.addView(blockedAppsContainer)

        // Listeners
        tabInstalledApps.setOnClickListener { if (!isExpired) switchSubTab(0) else showExpiredToast() }
        tabBlockedList.setOnClickListener   { if (!isExpired) switchSubTab(1) else showExpiredToast() }
        searchButton.setOnClickListener     { if (!isExpired) performSearch() else showExpiredToast() }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { suggestionsContainer.visibility = View.GONE }
            override fun afterTextChanged(s: Editable?) {}
        })

        scroll.addView(contentRoot)
        return scroll
    }

    fun applyExpiredMode(expired: Boolean) {
        isExpired = expired
        // Dim the entire tab — identical to DnsFragment's normalContent.alpha approach
        contentRoot.alpha = if (expired) 0.35f else 1.0f
    }

    private fun switchSubTab(tab: Int) {
        if (tab == 0) {
            tabInstalledApps.setBackgroundColor(GREEN); tabInstalledApps.setTextColor(TEXT_WHITE)
            tabBlockedList.setBackgroundColor(Color.parseColor("#2A2A3E")); tabBlockedList.setTextColor(TEXT_GREY)
            installedAppsContainer.visibility = View.VISIBLE; blockedAppsContainer.visibility = View.GONE
        } else {
            tabBlockedList.setBackgroundColor(GREEN); tabBlockedList.setTextColor(TEXT_WHITE)
            tabInstalledApps.setBackgroundColor(Color.parseColor("#2A2A3E")); tabInstalledApps.setTextColor(TEXT_GREY)
            installedAppsContainer.visibility = View.GONE; blockedAppsContainer.visibility = View.VISIBLE
        }
    }

    private fun performSearch() {
        val query = searchInput.text.toString().trim()
        if (query.isEmpty()) { Toast.makeText(requireContext(), "Please enter an app name", Toast.LENGTH_SHORT).show(); return }
        searchProgressBar.visibility = View.VISIBLE; searchStatusText.visibility = View.GONE
        suggestionsContainer.removeAllViews(); suggestionsContainer.visibility = View.GONE
        lifecycleScope.launch {
            val results = verificationService.searchPlayStore(query)
            searchProgressBar.visibility = View.GONE
            if (results.isEmpty()) {
                searchStatusText.text = "⚠️ No apps found. Double-check spelling or try package name"
                searchStatusText.setTextColor(ORANGE); searchStatusText.visibility = View.VISIBLE
            } else displaySearchResults(results)
        }
    }

    private fun displaySearchResults(results: List<AppSearchResult>) {
        suggestionsContainer.removeAllViews(); suggestionsContainer.visibility = View.VISIBLE
        suggestionsContainer.addView(makeLabel("✅ Found ${results.size} match${if (results.size == 1) "" else "es"}:", 14f, GREEN).apply { setPadding(0, 0, 0, dp(8)) })
        results.take(5).forEach { result ->
            val row = makeCard().apply { setBackgroundColor(Color.parseColor("#0D0D1A")) }
            val inner = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val textBlock = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            textBlock.addView(makeLabel(result.appName, 16f, TEXT_WHITE))
            textBlock.addView(makeLabel(result.packageName, 11f, TEXT_GREY))
            textBlock.addView(makeLabel(if (result.isInstalled) "✅ Installed" else "⏳ Not installed", 12f, if (result.isInstalled) GREEN else ORANGE))
            val addBtn = Button(requireContext()).apply { text = "Block"; setBackgroundColor(RED); setTextColor(TEXT_WHITE); textSize = 12f; setPadding(dp(12), dp(8), dp(12), dp(8)) }
            addBtn.setOnClickListener { confirmBlockApp(result) }
            inner.addView(textBlock); inner.addView(addBtn); row.addView(inner)
            suggestionsContainer.addView(row)
        }
    }

    private fun confirmBlockApp(app: AppSearchResult) {
        AlertDialog.Builder(requireContext()).setTitle("⚠️ Confirm Block")
            .setMessage("Block ${app.appName}?\n\nYou cannot unblock until commitment ends.")
            .setPositiveButton("Yes, Block It") { _, _ -> addToBlockList(app) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun addToBlockList(app: AppSearchResult) {
        lifecycleScope.launch {
            try {
                val blockedApp = IdentifierBlockedAppBuilder.newBuilder().setId(UUID.randomUUID().toString())
                    .setBlockedApp(BlockedAppBuilder.newBuilder().setPackageName(app.packageName).setAppName(app.appName)
                        .setIsSystemApp(false).setIsSuspended(true)
                        .setBlockReason(if (app.isInstalled) "User blocked" else "Pre-blocked")
                        .setDetectedDate(System.currentTimeMillis()).setIsInstalled(app.isInstalled).setIsPreBlocked(!app.isInstalled).build()).build()
                repository.create(blockedApp)
                if (app.isInstalled) blockAppNow(app.packageName)
                Toast.makeText(requireContext(), "✅ ${app.appName} blocked", Toast.LENGTH_SHORT).show()
                searchInput.text.clear(); suggestionsContainer.removeAllViews(); suggestionsContainer.visibility = View.GONE
                loadBlockedAppsData()
            } catch (e: Exception) { Log.e(TAG, "Error blocking app", e) }
        }
    }

    private fun blockAppNow(packageName: String) {
        try {
            val isNuclear = com.example.takwafortress.model.entities.BlockedApp.isNuclearApp(packageName)
            if (isNuclear) devicePolicyManager.setApplicationHidden(adminComponent, packageName, true)
            else           devicePolicyManager.setPackagesSuspended(adminComponent, arrayOf(packageName), true)
        } catch (e: Exception) { Log.e(TAG, "Failed to block $packageName", e) }
    }

    private fun loadBlockedAppsData() { lifecycleScope.launch { loadInstalledApps(); loadBlockedList() } }

    private suspend fun loadInstalledApps() {
        installedAppsContainer.removeAllViews()
        val allApps = verificationService.getAllInstallableApps()
        val blockedPkgs = repository.getAll().map { it.getPackageName() }.toSet()
        val available = allApps.filter { it.packageName !in blockedPkgs }
        if (available.isEmpty()) {
            installedAppsContainer.addView(makeLabel("✅ All apps managed", 14f, GREEN).apply { gravity = Gravity.CENTER; setPadding(dp(16), dp(40), dp(16), 0) })
        } else {
            installedAppsContainer.addView(makeLabel("${available.size} apps available to block", 14f, TEXT_GREY).apply { setPadding(0, 0, 0, dp(12)) })
            available.forEach { app ->
                val card = makeCard()
                val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val textBlock = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
                textBlock.addView(makeLabel(app.appName, 15f, TEXT_WHITE)); textBlock.addView(makeLabel(app.packageName, 11f, TEXT_GREY))
                val btn = Button(requireContext()).apply { text = "Block"; setBackgroundColor(RED); setTextColor(TEXT_WHITE); textSize = 13f; setPadding(dp(16), dp(6), dp(16), dp(6)) }
                btn.setOnClickListener { confirmBlockApp(app) }
                row.addView(textBlock); row.addView(btn); card.addView(row)
                installedAppsContainer.addView(card)
            }
        }
    }

    private suspend fun loadBlockedList() {
        blockedAppsContainer.removeAllViews()
        val blocked = repository.getAll()
        tabBlockedList.text = "🚫 Blocked List (${blocked.size})"
        if (blocked.isEmpty()) {
            blockedAppsContainer.addView(makeLabel("No apps blocked yet", 14f, TEXT_GREY).apply { gravity = Gravity.CENTER; setPadding(dp(16), dp(40), dp(16), 0) })
        } else {
            blocked.forEach { app ->
                val card = makeCard()
                card.addView(makeLabel(app.getAppName(), 15f, TEXT_WHITE))
                card.addView(makeLabel(app.getPackageName(), 11f, TEXT_GREY))
                card.addView(makeLabel(if (app.getIsInstalled()) "🚫 Currently blocked" else "⏳ Will block on install", 12f, if (app.getIsInstalled()) RED else ORANGE).apply { setPadding(0, dp(4), 0, 0) })
                card.addView(makeLabel("🔒 Locked until commitment ends", 11f, TEXT_GREY).apply { setPadding(0, dp(4), 0, 0) })
                blockedAppsContainer.addView(card)
            }
        }
    }

    private fun showExpiredToast() { Toast.makeText(requireContext(), "Commitment ended — this feature is disabled", Toast.LENGTH_SHORT).show() }
    private fun makeCard() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(CARD_BG); setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(10) }
    }
    private fun makeLabel(text: String, size: Float, color: Int, gravity: Int = Gravity.START) =
        TextView(requireContext()).apply { this.text = text; textSize = size; setTextColor(color); this.gravity = gravity }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}