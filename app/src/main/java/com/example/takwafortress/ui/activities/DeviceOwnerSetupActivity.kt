package com.example.takwafortress.ui.activities

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.example.takwafortress.R
import com.example.takwafortress.model.enums.ActivationMethod
import com.example.takwafortress.services.accessibility.PairingCodeAccessibilityService
import com.example.takwafortress.ui.viewmodels.DeviceOwnerSetupState
import com.example.takwafortress.ui.viewmodels.DeviceOwnerSetupViewModel

class DeviceOwnerSetupActivity : AppCompatActivity() {

    private lateinit var viewModel: DeviceOwnerSetupViewModel

    private lateinit var deviceBrandText      : TextView
    private lateinit var activationMethodText : TextView
    private lateinit var instructionsText     : TextView
    private lateinit var stepIndicatorText    : TextView
    private lateinit var openSettingsButton   : Button
    private lateinit var activateButton       : Button
    private lateinit var progressBar          : ProgressBar
    private lateinit var statusText           : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_owner_setup)
        viewModel = ViewModelProvider(this)[DeviceOwnerSetupViewModel::class.java]
        initViews()
        setupObservers()
        setupListeners()
        viewModel.checkDeviceOwnerStatus()
    }

    override fun onResume() {
        super.onResume()
        if (isAccessibilityServiceEnabled()) showReadyToScanState()
        // Handle result delivered by PairingCodeAccessibilityService
        handleServiceResult(intent)
    }

    /**
     * Called when already running and service re-launches with SINGLE_TOP.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleServiceResult(intent)
    }

    /**
     * Reads success/error extras sent by PairingCodeAccessibilityService
     * and posts them into the ViewModel so the UI reacts correctly.
     */
    private fun handleServiceResult(intent: Intent?) {
        intent ?: return
        if (!intent.hasExtra(PairingCodeAccessibilityService.EXTRA_ACTIVATION_SUCCESS)) return

        val success = intent.getBooleanExtra(PairingCodeAccessibilityService.EXTRA_ACTIVATION_SUCCESS, false)
        val error   = intent.getStringExtra(PairingCodeAccessibilityService.EXTRA_ACTIVATION_ERROR)

        // Clear so we don't handle again on next onResume
        intent.removeExtra(PairingCodeAccessibilityService.EXTRA_ACTIVATION_SUCCESS)
        intent.removeExtra(PairingCodeAccessibilityService.EXTRA_ACTIVATION_ERROR)

        if (success) {
            viewModel.onActivationSuccessFromService()
        } else {
            viewModel.onActivationErrorFromService(error ?: "Activation failed")
        }
    }

    private fun initViews() {
        deviceBrandText      = findViewById(R.id.deviceBrandText)
        activationMethodText = findViewById(R.id.activationMethodText)
        instructionsText     = findViewById(R.id.instructionsText)
        stepIndicatorText    = findViewById(R.id.stepIndicatorText)
        openSettingsButton   = findViewById(R.id.openSettingsButton)
        activateButton       = findViewById(R.id.activateButton)
        progressBar          = findViewById(R.id.progressBar)
        statusText           = findViewById(R.id.statusText)
    }

    private fun setupObservers() {
        viewModel.deviceBrand.observe(this) { brand ->
            deviceBrandText.text = "Device: ${brand.displayName}"
        }
        viewModel.activationMethod.observe(this) { method ->
            activationMethodText.text = "Method: ${method.displayName}"
            when (method) {
                ActivationMethod.KNOX         -> showKnoxFlow()
                ActivationMethod.WIRELESS_ADB -> showWirelessAdbFlow()
                else -> {}
            }
        }
        viewModel.setupState.observe(this) { state ->
            when (state) {
                is DeviceOwnerSetupState.Ready      -> { showLoading(false); updateInstructions(state.method) }
                is DeviceOwnerSetupState.Activating -> { showLoading(true); statusText.text = state.message }
                is DeviceOwnerSetupState.Success    -> { showLoading(false); showSuccessDialog() }
                is DeviceOwnerSetupState.AlreadyActive -> { showLoading(false); navigateToMain() }
                is DeviceOwnerSetupState.Error      -> { showLoading(false); showErrorDialog(state.message) }
            }
        }
    }

    private fun setupListeners() {
        openSettingsButton.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) showAccessibilityPermissionDialog()
            else openWirelessDebugging()
        }
        activateButton.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) showAccessibilityPermissionDialog()
            else openWirelessDebugging()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${packageName}/${PairingCodeAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains(serviceName, ignoreCase = true)
    }

    private fun showAccessibilityPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚡ One-time Setup Required")
            .setMessage(
                "To automatically read the pairing code from Wireless Debugging " +
                        "without you having to type anything, we need Accessibility permission.\n\n" +
                        "On the next screen:\n" +
                        "1. Find \"Taqwa Fortress\" in the list\n" +
                        "2. Tap it → toggle ON\n" +
                        "3. Press back to return here"
            )
            .setPositiveButton("Open Accessibility Settings") { _, _ -> openAccessibilitySettings() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun showReadyToScanState() {
        stepIndicatorText.text   = "✅ Auto-detection ready — open Wireless Debugging below"
        openSettingsButton.text  = "Open Wireless Debugging →"
        activateButton.isVisible = false
        statusText.text          = "🔍 Waiting for pairing popup to appear in Settings…"
        statusText.isVisible     = true
    }

    private fun openWirelessDebugging() {
        stepIndicatorText.text = "🔍 Watching for pairing popup…"
        statusText.text = "1. Enable Wireless Debugging\n2. Tap \"Pair device with pairing code\"\n3. The code will be detected automatically!"
        statusText.isVisible = true
        try {
            startActivity(Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"))
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            catch (e2: Exception) { Toast.makeText(this, "Open Developer Options manually", Toast.LENGTH_LONG).show() }
        }
    }

    private fun showKnoxFlow() {
        openSettingsButton.isVisible = false
        activateButton.text          = "Activate via Knox"
        activateButton.isVisible     = true
        stepIndicatorText.text       = "Step 1 / 1 — Tap to activate"
    }

    private fun showWirelessAdbFlow() {
        openSettingsButton.isVisible = true
        if (isAccessibilityServiceEnabled()) showReadyToScanState()
        else {
            openSettingsButton.text  = "Step 1: Grant Accessibility Permission →"
            activateButton.isVisible = false
            stepIndicatorText.text   = "One-time setup — tap above to begin"
        }
    }

    private fun updateInstructions(method: ActivationMethod) {
        instructionsText.text = when (method) {
            ActivationMethod.KNOX -> """
                🎉 Easy Knox Activation (10 seconds)
                1. Tap "Activate via Knox" below
                2. Accept the system prompt
                3. Done!
                ⚠️ Remove all Google / Samsung accounts first
            """.trimIndent()
            ActivationMethod.WIRELESS_ADB -> """
                ⚡ Fully Automatic Setup

                Step 1️⃣  Grant Accessibility permission (one-time only)
                Step 2️⃣  Open Wireless Debugging from this screen
                         Turn ON the toggle
                         Tap "Pair device with pairing code"
                Step 3️⃣  Done! We detect the code and activate automatically
                         You'll get a notification + this screen will open

                ⚠️ Remove ALL accounts first:
                   Settings → Accounts → Remove all
            """.trimIndent()
            else -> "Unknown method"
        }
    }

    private fun showLoading(loading: Boolean) {
        progressBar.isVisible        = loading
        openSettingsButton.isEnabled = !loading
        activateButton.isEnabled     = !loading
    }

    private fun showSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("🎉 Device Owner Activated!")
            .setMessage("Success! Your device is now under Taqwa protection.\n\n✅ Device Owner active\n✅ Uninstall blocked\n\nTap Continue to proceed.")
            .setPositiveButton("Continue") { _, _ -> navigateToMain() }
            .setCancelable(false)
            .show()
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("❌ Activation Failed")
            .setMessage(message)
            .setPositiveButton("Try Again") { _, _ -> openWirelessDebugging() }
            .setNegativeButton("Get Help") { _, _ ->
                Toast.makeText(this, "Help: support@taqwafortress.com", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}