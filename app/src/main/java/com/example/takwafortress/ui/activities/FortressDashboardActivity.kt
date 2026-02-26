package com.example.takwafortress.ui.activities

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.takwafortress.R
import com.example.takwafortress.services.core.FortressClearService
import com.example.takwafortress.ui.fragments.AppsFragment
import com.example.takwafortress.ui.fragments.AwarenessFragment
import com.example.takwafortress.ui.fragments.DashboardFragment
import com.example.takwafortress.ui.fragments.JourneyFragment
import com.example.takwafortress.ui.viewmodels.FortressStatusViewModel
import kotlinx.coroutines.launch
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class FortressDashboardActivity : AppCompatActivity() {

    private lateinit var viewModel: FortressStatusViewModel

    // Bottom nav tab views
    private lateinit var tabDashboard: LinearLayout
    private lateinit var tabApps: LinearLayout
    private lateinit var tabAwareness: LinearLayout
    private lateinit var tabJourney: LinearLayout

    private lateinit var iconDashboard: TextView
    private lateinit var iconApps: TextView
    private lateinit var iconAwareness: TextView
    private lateinit var iconJourney: TextView

    private lateinit var labelDashboard: TextView
    private lateinit var labelApps: TextView
    private lateinit var labelAwareness: TextView
    private lateinit var labelJourney: TextView

    private var activeTabIndex = 0
    private var clearAlreadyTriggered = false

    companion object {
        const val EXTRA_EXPIRED = "extra_expired"
        private const val TAG = "FortressDashboard"

        private val COLOR_BG         = Color.parseColor("#0F0F1A")
        private val COLOR_NAV_BG     = Color.parseColor("#13131F")
        private val COLOR_ACTIVE     = Color.parseColor("#4CAF50")
        private val COLOR_INACTIVE   = Color.parseColor("#3A3A5C")
        private val COLOR_NAV_BORDER = Color.parseColor("#1E1E30")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startInExpiredMode = intent.getBooleanExtra(EXTRA_EXPIRED, false)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return FortressStatusViewModel(this@FortressDashboardActivity) as T
            }
        })[FortressStatusViewModel::class.java]

        buildLayout(startInExpiredMode)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, DashboardFragment.newInstance(startInExpiredMode), "dashboard")
                .add(R.id.fragmentContainer, AppsFragment.newInstance(startInExpiredMode),      "apps")
                .add(R.id.fragmentContainer, AwarenessFragment.newInstance(),                   "awareness")
                .add(R.id.fragmentContainer, JourneyFragment.newInstance(),                     "journey")
                .commitNow()
        }

        showTab(0)

        viewModel.isCommitmentExpired.observe(this) { expired ->
            if (expired && !clearAlreadyTriggered) {
                Log.d(TAG, "isCommitmentExpired → triggering FortressClearService")
                triggerClear()
            }
        }

        if (startInExpiredMode) {
            Log.d(TAG, "Launched in expired mode → triggering FortressClearService")
            triggerClear()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadCommitmentFromFirestore()
    }

    // ═══════════════════════════════════════════
    // LAYOUT
    // ═══════════════════════════════════════════

    private fun buildLayout(startInExpiredMode: Boolean) {
        // Edge-to-edge so we can draw behind system bars
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Fragment container fills all available space
        val fragmentContainer = FrameLayout(this).apply {
            id = R.id.fragmentContainer
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(fragmentContainer)

        // Top separator line
        val separator = android.view.View(this).apply {
            setBackgroundColor(COLOR_NAV_BORDER)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            )
        }
        root.addView(separator)

        // Bottom navigation bar — respects system gesture area
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(COLOR_NAV_BG)
            val bottomInset = getSystemNavigationBarHeight()
            setPadding(0, dp(8), 0, bottomInset + dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        tabDashboard = makeNavTab("🛡️", "Fortress")
        tabApps      = makeNavTab("📱", "Apps")
        tabAwareness = makeNavTab("📖", "Awareness")
        tabJourney   = makeNavTab("🗺️", "Journey")

        iconDashboard  = tabDashboard.getChildAt(0) as TextView
        labelDashboard = tabDashboard.getChildAt(1) as TextView
        iconApps       = tabApps.getChildAt(0) as TextView
        labelApps      = tabApps.getChildAt(1) as TextView
        iconAwareness  = tabAwareness.getChildAt(0) as TextView
        labelAwareness = tabAwareness.getChildAt(1) as TextView
        iconJourney    = tabJourney.getChildAt(0) as TextView
        labelJourney   = tabJourney.getChildAt(1) as TextView

        tabDashboard.setOnClickListener { showTab(0) }
        tabApps.setOnClickListener      { showTab(1) }
        tabAwareness.setOnClickListener { showTab(2) }
        tabJourney.setOnClickListener   { showTab(3) }

        navBar.addView(tabDashboard)
        navBar.addView(tabApps)
        navBar.addView(tabAwareness)
        navBar.addView(tabJourney)

        root.addView(navBar)
        setContentView(root)
    }

    private fun makeNavTab(icon: String, label: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f)

            val iconView = TextView(this@FortressDashboardActivity).apply {
                text = icon
                textSize = 20f
                gravity = Gravity.CENTER
            }
            val labelView = TextView(this@FortressDashboardActivity).apply {
                text = label
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(COLOR_INACTIVE)
                letterSpacing = 0.05f
            }
            addView(iconView)
            addView(labelView)
        }
    }

    // ═══════════════════════════════════════════
    // TAB NAVIGATION
    // ═══════════════════════════════════════════

    fun showTab(index: Int) {
        activeTabIndex = index
        val fm = supportFragmentManager

        val dashboard = fm.findFragmentByTag("dashboard") ?: return
        val apps      = fm.findFragmentByTag("apps")      ?: return
        val awareness = fm.findFragmentByTag("awareness") ?: return
        val journey   = fm.findFragmentByTag("journey")   ?: return

        fm.beginTransaction().apply {
            if (index == 0) show(dashboard) else hide(dashboard)
            if (index == 1) show(apps)      else hide(apps)
            if (index == 2) show(awareness) else hide(awareness)
            if (index == 3) show(journey)   else hide(journey)
        }.commit()

        // Update tab colors
        val tabs = listOf(
            Triple(tabDashboard,  iconDashboard,  labelDashboard),
            Triple(tabApps,       iconApps,       labelApps),
            Triple(tabAwareness,  iconAwareness,  labelAwareness),
            Triple(tabJourney,    iconJourney,    labelJourney)
        )
        tabs.forEachIndexed { i, (_, _, label) ->
            label.setTextColor(if (i == index) COLOR_ACTIVE else COLOR_INACTIVE)
        }
    }

    // ═══════════════════════════════════════════
    // FORTRESS CLEAR
    // ═══════════════════════════════════════════

    private fun triggerClear() {
        if (clearAlreadyTriggered) return
        clearAlreadyTriggered = true
        lifecycleScope.launch {
            Log.i(TAG, "FortressClearService starting...")
            val result = FortressClearService(this@FortressDashboardActivity).clearEverything()
            Log.i(TAG, "FortressClearService result: $result")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Toast.makeText(this, "Cannot exit fortress", Toast.LENGTH_SHORT).show()
    }

    private fun getSystemNavigationBarHeight(): Int {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}