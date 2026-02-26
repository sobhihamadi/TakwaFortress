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

    private var isCheckInProgress = false
    private var isUpdatePending   = false

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

        startUpdateCheckThenRoute()
    }

    override fun onResume() {
        super.onResume()
        // Only re-trigger if user came back after opening installer but cancelled
        if (isUpdatePending && !isFinishing) {
            isUpdatePending = false
            startUpdateCheckThenRoute()
        }
    }

    private fun startUpdateCheckThenRoute() {
        // ✅ Hard guard — if a check is already running, do nothing
        if (isCheckInProgress) return
        isCheckInProgress = true

        lifecycleScope.launch {
            try {
                val checker = UpdateChecker(this@MainActivity)
                val update  = checker.checkForUpdate()

                if (update != null) {
                    isUpdatePending   = true
                    isCheckInProgress = false   // allow re-check after installer returns
                    checker.showUpdateDialog(this@MainActivity, update)
                } else {
                    isUpdatePending   = false
                    isCheckInProgress = false
                    viewModel.clearCache()
                    viewModel.resolveRoute()
                }
            } catch (e: Exception) {
                isCheckInProgress = false
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