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

    // Track if we already ran routing so onResume doesn't double-navigate
    private var routingStarted = false

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
        // ✅ If the user went to the browser/file manager to download the APK
        // and came back WITHOUT installing, re-run the update check so the
        // dialog reappears and they can't bypass the update.
        // But only if we haven't already navigated away.
        if (routingStarted && !isFinishing) {
            startUpdateCheckThenRoute()
        }
    }

    private fun startUpdateCheckThenRoute() {
        routingStarted = true
        lifecycleScope.launch {
            val checker = UpdateChecker(this@MainActivity)
            val update  = checker.checkForUpdate()

            if (update != null) {
                // ✅ Show the update dialog.
                // We do NOT call resolveRoute() here — the user must update first.
                // When they install the new APK the app restarts fresh.
                checker.showUpdateDialog(this@MainActivity, update)
                // Don't return — do NOT navigate while update is pending
            } else {
                // No update needed — proceed with normal routing
                viewModel.clearCache()   // clear cache so routing reads fresh Firestore data
                viewModel.resolveRoute()
            }
        }
    }

    private fun navigateTo(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass))
        finish()
    }
}