package com.example.takwafortress.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.takwafortress.R
import com.example.takwafortress.services.core.UpdateChecker
import com.example.takwafortress.ui.viewmodels.MainViewModel
import com.example.takwafortress.ui.viewmodels.RouteDestination
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel

    // ✅ FIX 1: Track state properly to prevent double dialog
    private var updateCheckDone = false
    private var isUpdatePending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        viewModel.destination.observe(this) { destination ->
            when (destination) {
                is RouteDestination.Welcome -> navigateTo(WelcomeActivity::class.java)

                is RouteDestination.CommitmentSelection ->
                    navigateTo(CommitmentSelectionActivity::class.java)

                is RouteDestination.DeviceOwnerSetup ->
                    navigateTo(DeviceOwnerSetupActivity::class.java)

                is RouteDestination.Dashboard ->
                    navigateTo(FortressDashboardActivity::class.java)

                is RouteDestination.ExpiredDashboard -> {
                    startActivity(
                        Intent(this, FortressDashboardActivity::class.java).apply {
                            putExtra(FortressDashboardActivity.EXTRA_EXPIRED, true)
                        }
                    )
                    finish()
                }

                is RouteDestination.CommitmentComplete -> {
                    startActivity(
                        Intent(this, FortressDashboardActivity::class.java).apply {
                            putExtra(FortressDashboardActivity.EXTRA_EXPIRED, true)
                        }
                    )
                    finish()
                }

                is RouteDestination.TrialExpired -> {
                    startActivity(
                        Intent(this, FortressDashboardActivity::class.java).apply {
                            putExtra(FortressDashboardActivity.EXTRA_EXPIRED, true)
                        }
                    )
                    finish()
                }

                is RouteDestination.SubscriptionExpired ->
                    navigateTo(SubscriptionExpiredActivity::class.java)

                is RouteDestination.UnauthorizedDevice -> {
                    startActivity(
                        Intent(this, UnauthorizedDeviceActivity::class.java).apply {
                            putExtra("CURRENT_DEVICE_ID", destination.currentDeviceId)
                            putExtra("STORED_DEVICE_ID", destination.storedDeviceId)
                        }
                    )
                    finish()
                }
            }
        }

        // ✅ FIX 1: Only run on first onCreate, never on resume
        if (savedInstanceState == null) {
            startUpdateCheckThenRoute()
        }
    }

    override fun onResume() {
        super.onResume()
        // ✅ FIX 1: Only re-check if user came back from installer WITHOUT installing
        // (update was pending but app is still running = they cancelled the install)
        // Do NOT re-check if this is a normal resume or first launch
        if (updateCheckDone && isUpdatePending && !isFinishing) {
            isUpdatePending = false
            startUpdateCheckThenRoute()
        }
    }

    private fun startUpdateCheckThenRoute() {
        lifecycleScope.launch {
            val checker = UpdateChecker(this@MainActivity)
            val update  = checker.checkForUpdate()

            updateCheckDone = true

            if (update != null) {
                // ✅ Mark update as pending so onResume can re-show if user cancels install
                isUpdatePending = true
                checker.showUpdateDialog(this@MainActivity, update)
            } else {
                isUpdatePending = false
                viewModel.clearCache()
                viewModel.resolveRoute()
            }
        }
    }

    private fun navigateTo(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass))
        finish()
    }
}