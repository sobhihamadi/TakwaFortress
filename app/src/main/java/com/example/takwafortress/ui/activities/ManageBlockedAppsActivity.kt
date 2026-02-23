package com.example.takwafortress.ui.activities

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.takwafortress.repository.implementations.LocalBlockedAppRepository
import com.example.takwafortress.services.verification.GooglePlayVerificationService
import com.example.takwafortress.services.verification.AppSearchResult
import com.example.takwafortress.model.builders.IdentifierBlockedAppBuilder
import com.example.takwafortress.model.builders.BlockedAppBuilder
import com.example.takwafortress.services.core.DeviceOwnerService
import kotlinx.coroutines.launch
import java.util.UUID
import android.util.Log

private val BG_DARK = Color.parseColor("#1A1A2E")
private val CARD_BG = Color.parseColor("#16213E")
private val GREEN = Color.parseColor("#4CAF50")
private val ORANGE = Color.parseColor("#FF9800")
private val RED = Color.parseColor("#F44336")
private val TEXT_WHITE = Color.WHITE
private val TEXT_GREY = Color.parseColor("#9E9E9E")

/**
 * ✅ FINAL: Manage Blocked Apps Activity
 * - Shows action bar with back button
 * - Actually blocks apps using Device Owner
 * - Two tabs: Installed Apps + Blocked List
 */
class ManageBlockedAppsActivity : AppCompatActivity() {

    private lateinit var repository: LocalBlockedAppRepository
    private lateinit var verificationService: GooglePlayVerificationService
    private lateinit var deviceOwnerService: DeviceOwnerService
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: android.content.ComponentName

    private lateinit var searchInput: EditText
    private lateinit var searchButton: Button
    private lateinit var installedAppsContainer: LinearLayout
    private lateinit var blockedAppsContainer: LinearLayout
    private lateinit var suggestionsContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var tabInstalled: Button
    private lateinit var tabBlocked: Button

    private var currentTab = 0
    private var searchResults: List<AppSearchResult> = emptyList()

    companion object {
        private const val TAG = "ManageBlockedApps"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = LocalBlockedAppRepository(this)
        verificationService = GooglePlayVerificationService(this)
        deviceOwnerService = DeviceOwnerService(this)
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = android.content.ComponentName(
            this,
            com.example.takwafortress.receivers.DeviceAdminReceiver::class.java
        )

        // ✅ FIXED: Enable action bar with back button
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "🚫 Manage Blocked Apps"
        }

        if (!deviceOwnerService.isDeviceOwner()) {
            Toast.makeText(
                this,
                "⚠️ Device Owner required to block apps",
                Toast.LENGTH_LONG
            ).show()
            Log.e(TAG, "Not Device Owner - blocking will fail")
        }

        val root = buildUI()
        setContentView(root)

        loadData()
    }

    // ✅ FIXED: Handle back button in action bar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun buildUI(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG_DARK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // Header (subtitle - main title is in action bar)
        container.addView(makeLabel(
            "Block apps now or pre-block apps to auto-block when installed",
            14f, TEXT_GREY
        ).apply { setPadding(0, 0, 0, dp(20)) })

        // Search section
        val searchCard = makeCard()
        searchCard.addView(makeLabel("➕ Add App to Block List", 18f, TEXT_WHITE))
        searchCard.addView(makeLabel(
            "Search by name or enter package name",
            12f, TEXT_GREY
        ).apply { setPadding(0, dp(4), 0, dp(12)) })

        searchInput = EditText(this).apply {
            hint = "Enter app name (e.g., Twitter, Instagram)"
            setTextColor(TEXT_WHITE)
            setHintTextColor(TEXT_GREY)
            setBackgroundColor(Color.parseColor("#0D0D1A"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply { bottomMargin = dp(12) }
        }
        searchCard.addView(searchInput)

        suggestionsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        searchCard.addView(suggestionsContainer)

        searchButton = makeGreenButton("🔍 Search & Verify")
        searchCard.addView(searchButton)

        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                topMargin = dp(12)
            }
        }
        searchCard.addView(progressBar)

        statusText = makeLabel("", 14f, TEXT_GREY).apply {
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }
        searchCard.addView(statusText)

        container.addView(searchCard)

        // Tabs
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply { topMargin = dp(16) }
        }

        tabInstalled = Button(this).apply {
            text = "📱 Installed Apps"
            setBackgroundColor(GREEN)
            setTextColor(TEXT_WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { switchTab(0) }
        }
        tabBlocked = Button(this).apply {
            text = "🚫 Blocked List (0)"
            setBackgroundColor(Color.parseColor("#2A2A3E"))
            setTextColor(TEXT_GREY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { switchTab(1) }
        }

        tabBar.addView(tabInstalled)
        tabBar.addView(tabBlocked)
        container.addView(tabBar)

        // Content containers
        installedAppsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }

        blockedAppsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }

        container.addView(installedAppsContainer)
        container.addView(blockedAppsContainer)

        scroll.addView(container)

        searchButton.setOnClickListener { performSearch() }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                suggestionsContainer.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        return scroll
    }

    private fun switchTab(tab: Int) {
        currentTab = tab

        if (tab == 0) {
            tabInstalled.setBackgroundColor(GREEN)
            tabInstalled.setTextColor(TEXT_WHITE)
            tabBlocked.setBackgroundColor(Color.parseColor("#2A2A3E"))
            tabBlocked.setTextColor(TEXT_GREY)
            installedAppsContainer.visibility = View.VISIBLE
            blockedAppsContainer.visibility = View.GONE
        } else {
            tabBlocked.setBackgroundColor(GREEN)
            tabBlocked.setTextColor(TEXT_WHITE)
            tabInstalled.setBackgroundColor(Color.parseColor("#2A2A3E"))
            tabInstalled.setTextColor(TEXT_GREY)
            installedAppsContainer.visibility = View.GONE
            blockedAppsContainer.visibility = View.VISIBLE
        }
    }

    private fun performSearch() {
        val query = searchInput.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter an app name", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        statusText.visibility = View.GONE
        suggestionsContainer.removeAllViews()
        suggestionsContainer.visibility = View.GONE

        lifecycleScope.launch {
            val results = verificationService.searchPlayStore(query)
            searchResults = results

            progressBar.visibility = View.GONE

            if (results.isEmpty()) {
                statusText.text = "⚠️ No apps found. Double-check spelling or try package name (e.g., com.twitter.android)"
                statusText.setTextColor(ORANGE)
                statusText.visibility = View.VISIBLE
            } else {
                displaySearchResults(results)
            }
        }
    }

    private fun displaySearchResults(results: List<AppSearchResult>) {
        suggestionsContainer.removeAllViews()
        suggestionsContainer.visibility = View.VISIBLE

        suggestionsContainer.addView(makeLabel(
            "✅ Found ${results.size} match${if (results.size == 1) "" else "es"}:",
            14f, GREEN
        ).apply { setPadding(0, 0, 0, dp(8)) })

        results.take(5).forEach { result ->
            val resultRow = makeCard().apply {
                setBackgroundColor(Color.parseColor("#0D0D1A"))
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }

            val textBlock = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val nameText = makeLabel(result.appName, 16f, TEXT_WHITE)
            val packageText = makeLabel(result.packageName, 11f, TEXT_GREY)
            val statusLabel = makeLabel(
                if (result.isInstalled) "✅ Installed" else "⏳ Not installed (will block on install)",
                12f,
                if (result.isInstalled) GREEN else ORANGE
            )

            textBlock.addView(nameText)
            textBlock.addView(packageText)
            textBlock.addView(statusLabel)

            val addButton = Button(this).apply {
                text = "Add to Block List"
                setBackgroundColor(RED)
                setTextColor(TEXT_WHITE)
                textSize = 12f
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }

            addButton.setOnClickListener {
                confirmBlockApp(result)
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(textBlock)
            row.addView(addButton)

            resultRow.addView(row)
            suggestionsContainer.addView(resultRow)
        }
    }

    private fun confirmBlockApp(app: AppSearchResult) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Confirm Block")
            .setMessage(buildString {
                append("You're about to block:\n\n")
                append("📱 ${app.appName}\n")
                append("📦 ${app.packageName}\n\n")
                if (app.isInstalled) {
                    append("This app is INSTALLED and will be blocked immediately.\n\n")
                } else {
                    append("This app is NOT installed yet. It will be auto-blocked if you try to install it later.\n\n")
                }
                append("⏰ You cannot unblock until your commitment period ends.\n\n")
                append("Are you sure?")
            })
            .setPositiveButton("Yes, Block It") { _, _ ->
                addToBlockList(app)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addToBlockList(app: AppSearchResult) {
        lifecycleScope.launch {
            try {
                val isBlocked = repository.isAppBlocked(app.packageName)
                if (isBlocked) {
                    Toast.makeText(this@ManageBlockedAppsActivity, "${app.appName} is already blocked", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val blockedApp = IdentifierBlockedAppBuilder.newBuilder()
                    .setId(UUID.randomUUID().toString())
                    .setBlockedApp(
                        BlockedAppBuilder.newBuilder()
                            .setPackageName(app.packageName)
                            .setAppName(app.appName)
                            .setIsSystemApp(false)
                            .setIsSuspended(true)
                            .setBlockReason(if (app.isInstalled) "User blocked" else "Pre-blocked (not installed)")
                            .setDetectedDate(System.currentTimeMillis())
                            .setIsInstalled(app.isInstalled)
                            .setIsPreBlocked(!app.isInstalled)
                            .build()
                    )
                    .build()

                repository.create(blockedApp)

                if (app.isInstalled) {
                    val success = blockAppNow(app.packageName)
                    if (success) {
                        Log.i(TAG, "✅ Successfully blocked ${app.appName}")
                        Toast.makeText(
                            this@ManageBlockedAppsActivity,
                            "✅ ${app.appName} blocked successfully",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Log.e(TAG, "❌ Failed to block ${app.appName}")
                        Toast.makeText(
                            this@ManageBlockedAppsActivity,
                            "⚠️ ${app.appName} added to list but blocking failed. Check Device Owner status.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@ManageBlockedAppsActivity,
                        "✅ ${app.appName} will be blocked when installed",
                        Toast.LENGTH_LONG
                    ).show()
                }

                searchInput.text.clear()
                suggestionsContainer.removeAllViews()
                suggestionsContainer.visibility = View.GONE

                loadData()

            } catch (e: Exception) {
                Log.e(TAG, "Error adding to blocklist", e)
                Toast.makeText(this@ManageBlockedAppsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun blockAppNow(packageName: String): Boolean {
        if (!deviceOwnerService.isDeviceOwner()) {
            Log.e(TAG, "Cannot block - not Device Owner")
            return false
        }

        return try {
            val isNuclear = com.example.takwafortress.model.entities.BlockedApp.isNuclearApp(packageName)

            if (isNuclear) {
                devicePolicyManager.setApplicationHidden(adminComponent, packageName, true)
                Log.i(TAG, "Hidden nuclear app: $packageName")
            } else {
                devicePolicyManager.setPackagesSuspended(adminComponent, arrayOf(packageName), true)
                Log.i(TAG, "Suspended app: $packageName")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to block app: $packageName", e)
            false
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            loadInstalledApps()
            loadBlockedApps()
        }
    }

    private suspend fun loadInstalledApps() {
        installedAppsContainer.removeAllViews()

        val allApps = verificationService.getAllInstallableApps()
        val blockedPackages = repository.getAll().map { it.getPackageName() }.toSet()

        val availableApps = allApps.filter { it.packageName !in blockedPackages }

        if (availableApps.isEmpty()) {
            installedAppsContainer.addView(makeLabel(
                "✅ All apps are already managed",
                14f, GREEN
            ).apply {
                setPadding(dp(16), dp(40), dp(16), 0)
                gravity = Gravity.CENTER
            })
        } else {
            installedAppsContainer.addView(makeLabel(
                "${availableApps.size} apps available to block",
                14f, TEXT_GREY
            ).apply { setPadding(0, 0, 0, dp(12)) })

            availableApps.forEach { app ->
                installedAppsContainer.addView(makeInstalledAppRow(app))
            }
        }
    }

    private suspend fun loadBlockedApps() {
        blockedAppsContainer.removeAllViews()

        val blocked = repository.getAll()

        tabBlocked.text = "🚫 Blocked List (${blocked.size})"

        if (blocked.isEmpty()) {
            blockedAppsContainer.addView(makeLabel(
                "No apps blocked yet",
                14f, TEXT_GREY
            ).apply {
                setPadding(dp(16), dp(40), dp(16), 0)
                gravity = Gravity.CENTER
            })
        } else {
            blockedAppsContainer.addView(makeLabel(
                "${blocked.size} app${if (blocked.size == 1) "" else "s"} currently blocked",
                14f, TEXT_GREY
            ).apply { setPadding(0, 0, 0, dp(12)) })

            blocked.forEach { app ->
                blockedAppsContainer.addView(makeBlockedAppRow(app))
            }
        }
    }

    private fun makeInstalledAppRow(app: AppSearchResult): View {
        val card = makeCard()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val textBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        textBlock.addView(makeLabel(app.appName, 15f, TEXT_WHITE))
        textBlock.addView(makeLabel(app.packageName, 11f, TEXT_GREY))

        val blockBtn = Button(this).apply {
            text = "Block"
            setBackgroundColor(RED)
            setTextColor(TEXT_WHITE)
            textSize = 13f
            setPadding(dp(16), dp(6), dp(16), dp(6))
        }

        blockBtn.setOnClickListener {
            confirmBlockApp(app)
        }

        row.addView(textBlock)
        row.addView(blockBtn)
        card.addView(row)

        return card
    }

    private fun makeBlockedAppRow(app: com.example.takwafortress.model.entities.IdentifierBlockedApp): View {
        val card = makeCard()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        col.addView(makeLabel(app.getAppName(), 15f, TEXT_WHITE))
        col.addView(makeLabel(app.getPackageName(), 11f, TEXT_GREY))

        val statusText = if (app.getIsInstalled()) {
            "🚫 Currently blocked"
        } else {
            "⏳ Will block on install"
        }

        col.addView(makeLabel(statusText, 12f, if (app.getIsInstalled()) RED else ORANGE).apply {
            setPadding(0, dp(4), 0, 0)
        })

        col.addView(makeLabel(
            "🔒 Locked until commitment ends",
            11f, TEXT_GREY
        ).apply { setPadding(0, dp(4), 0, 0) })

        card.addView(col)
        return card
    }

    private fun makeCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(CARD_BG)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = dp(10) }
    }

    private fun makeLabel(text: String, size: Float, color: Int): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
        }

    private fun makeGreenButton(label: String): Button = Button(this).apply {
        text = label
        textSize = 15f
        setTextColor(TEXT_WHITE)
        setBackgroundColor(GREEN)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(50)
        ).also { it.topMargin = dp(8) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ✅ FIXED: Handle hardware back button
    override fun onBackPressed() {
        finish()
    }
}