package com.example.takwafortress.ui.activities

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.takwafortress.R
import com.example.takwafortress.services.core.FortressClearService
import com.example.takwafortress.ui.fragments.AppsFragment
import com.example.takwafortress.ui.fragments.DashboardFragment
import com.example.takwafortress.ui.fragments.DnsFragment
import com.example.takwafortress.ui.fragments.SettingsFragment
import com.example.takwafortress.ui.viewmodels.FortressStatusViewModel
import kotlinx.coroutines.launch

private val TAB_ACTIVE   = Color.parseColor("#4CAF50")
private val TAB_INACTIVE = Color.parseColor("#2A2A4A")

class FortressDashboardActivity : AppCompatActivity() {

    private lateinit var viewModel: FortressStatusViewModel

    private lateinit var btnDashboard: Button
    private lateinit var btnApps: Button
    private lateinit var btnDns: Button
    private lateinit var btnSettings: Button

    private var activeTabIndex = 0

    // Prevents FortressClearService from being called twice if both
    // startInExpiredMode and isCommitmentExpired fire in the same session.
    private var clearAlreadyTriggered = false

    companion object {
        const val EXTRA_EXPIRED = "extra_expired"
        private const val TAG = "FortressDashboard"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fortress_dashboard)

        val startInExpiredMode = intent.getBooleanExtra(EXTRA_EXPIRED, false)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return FortressStatusViewModel(this@FortressDashboardActivity) as T
            }
        })[FortressStatusViewModel::class.java]

        btnDashboard = findViewById(R.id.btnTabDashboard)
        btnApps      = findViewById(R.id.btnTabApps)
        btnDns       = findViewById(R.id.btnTabDns)
        btnSettings  = findViewById(R.id.btnTabSettings)

        btnDashboard.setOnClickListener { showTab(0) }
        btnApps.setOnClickListener      { showTab(1) }
        btnDns.setOnClickListener       { showTab(2) }
        btnSettings.setOnClickListener  { showTab(3) }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, DashboardFragment.newInstance(startInExpiredMode), "dashboard")
                .add(R.id.fragmentContainer, AppsFragment.newInstance(startInExpiredMode),      "apps")
                .add(R.id.fragmentContainer, DnsFragment.newInstance(startInExpiredMode),        "dns")
                .add(R.id.fragmentContainer, SettingsFragment.newInstance(startInExpiredMode),   "settings")
                .commitNow()
        }

        showTab(0)

        // ── Case 1: Plan expired while app is open ────────────────────────────
        // The countdown in FortressStatusViewModel hits zero and posts true.
        viewModel.isCommitmentExpired.observe(this) { expired ->
            if (expired && !clearAlreadyTriggered) {
                Log.d(TAG, "⏰ isCommitmentExpired → triggering FortressClearService")
                triggerClear()
            }
        }

        // ── Case 2: App opened after plan already ended ───────────────────────
        // MainViewModel routed here with EXTRA_EXPIRED = true.
        if (startInExpiredMode) {
            Log.d(TAG, "⏰ Launched in expired mode → triggering FortressClearService")
            triggerClear()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check expiry each time the user returns (e.g. from background).
        viewModel.loadCommitmentFromFirestore()
    }

    // ═══════════════════════════════════════════
    // FORTRESS CLEAR
    // ═══════════════════════════════════════════

    private fun triggerClear() {
        if (clearAlreadyTriggered) return
        clearAlreadyTriggered = true

        lifecycleScope.launch {
            Log.i(TAG, "═══════════════════════════════════════")
            Log.i(TAG, "FortressClearService starting...")
            Log.i(TAG, "═══════════════════════════════════════")
            val result = FortressClearService(this@FortressDashboardActivity).clearEverything()
            Log.i(TAG, "FortressClearService result: $result")
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
        val dns       = fm.findFragmentByTag("dns")       ?: return
        val settings  = fm.findFragmentByTag("settings")  ?: return

        fm.beginTransaction().apply {
            if (index == 0) show(dashboard) else hide(dashboard)
            if (index == 1) show(apps)      else hide(apps)
            if (index == 2) show(dns)       else hide(dns)
            if (index == 3) show(settings)  else hide(settings)
        }.commit()

        btnDashboard.setBackgroundColor(if (index == 0) TAB_ACTIVE else TAB_INACTIVE)
        btnApps.setBackgroundColor(     if (index == 1) TAB_ACTIVE else TAB_INACTIVE)
        btnDns.setBackgroundColor(      if (index == 2) TAB_ACTIVE else TAB_INACTIVE)
        btnSettings.setBackgroundColor( if (index == 3) TAB_ACTIVE else TAB_INACTIVE)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Toast.makeText(this, "Cannot exit fortress dashboard", Toast.LENGTH_SHORT).show()
    }
}