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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ✅ ViewModel initialized BEFORE anything uses it
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // ✅ Observe destinations BEFORE resolveRoute is called
        viewModel.destination.observe(this) { destination ->
            when (destination) {
                is RouteDestination.Welcome -> {
                    navigateTo(WelcomeActivity::class.java)
                }
                is RouteDestination.CommitmentSelection -> {
                    navigateTo(CommitmentSelectionActivity::class.java)
                }
                is RouteDestination.DeviceOwnerSetup -> {
                    navigateTo(DeviceOwnerSetupActivity::class.java)
                }
                is RouteDestination.Dashboard -> {
                    navigateTo(FortressDashboardActivity::class.java)
                }
                is RouteDestination.ExpiredDashboard -> {
                    val intent = Intent(this, FortressDashboardActivity::class.java).apply {
                        putExtra(FortressDashboardActivity.EXTRA_EXPIRED, true)
                    }
                    startActivity(intent)
                    finish()
                }
                is RouteDestination.CommitmentComplete -> {
                    val intent = Intent(this, FortressDashboardActivity::class.java).apply {
                        putExtra(FortressDashboardActivity.EXTRA_EXPIRED, true)
                    }
                    startActivity(intent)
                    finish()
                }
                is RouteDestination.TrialExpired -> {
                    val intent = Intent(this, FortressDashboardActivity::class.java).apply {
                        putExtra(FortressDashboardActivity.EXTRA_EXPIRED, true)
                    }
                    startActivity(intent)
                    finish()
                }
                is RouteDestination.SubscriptionExpired -> {
                    navigateTo(SubscriptionExpiredActivity::class.java)
                }
                is RouteDestination.UnauthorizedDevice -> {
                    val intent = Intent(this, UnauthorizedDeviceActivity::class.java).apply {
                        putExtra("CURRENT_DEVICE_ID", destination.currentDeviceId)
                        putExtra("STORED_DEVICE_ID", destination.storedDeviceId)
                    }
                    startActivity(intent)
                    finish()
                }
            }
        }

        // ✅ Check for update first, then route — single call only
        lifecycleScope.launch {
            val checker = UpdateChecker(this@MainActivity)
            val update = checker.checkForUpdate()
            if (update != null) {
                checker.showUpdateDialog(this@MainActivity, update)
                return@launch
            }
            viewModel.resolveRoute()
        }
    }

    private fun navigateTo(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass))
        finish()
    }
}