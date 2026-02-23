package com.example.takwafortress.ui.activities

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.example.takwafortress.R
import com.example.takwafortress.model.enums.ActivationMethod
import com.example.takwafortress.ui.viewmodels.DeviceOwnerSetupViewModel
import com.example.takwafortress.ui.viewmodels.DeviceOwnerSetupState

class DeviceOwnerSetupActivity : AppCompatActivity() {

    private lateinit var viewModel: DeviceOwnerSetupViewModel

    private lateinit var deviceBrandText      : TextView
    private lateinit var activationMethodText : TextView
    private lateinit var instructionsText     : TextView
    private lateinit var stepIndicatorText    : TextView
    private lateinit var inputGroup           : android.widget.LinearLayout
    private lateinit var pairingCodeInput     : EditText
    private lateinit var pairingPortInput     : EditText
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

    private fun initViews() {
        deviceBrandText      = findViewById(R.id.deviceBrandText)
        activationMethodText = findViewById(R.id.activationMethodText)
        instructionsText     = findViewById(R.id.instructionsText)
        stepIndicatorText    = findViewById(R.id.stepIndicatorText)
        inputGroup           = findViewById(R.id.inputGroup)
        pairingCodeInput     = findViewById(R.id.pairingCodeInput)
        pairingPortInput     = findViewById(R.id.pairingPortInput)
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
                is DeviceOwnerSetupState.Ready -> {
                    showLoading(false)
                    updateInstructions(state.method)
                }
                is DeviceOwnerSetupState.Activating -> {
                    showLoading(true)
                    statusText.text = state.message
                }
                is DeviceOwnerSetupState.Success -> {
                    showLoading(false)
                    showSuccessDialog()
                }
                is DeviceOwnerSetupState.AlreadyActive -> {
                    showLoading(false)
                    // Device Owner already set — let MainViewModel decide where to go
                    navigateToMain()
                }
                is DeviceOwnerSetupState.Error -> {
                    showLoading(false)
                    showErrorDialog(state.message)
                }
            }
        }
    }

    private fun setupListeners() {
        openSettingsButton.setOnClickListener {
            openDeveloperOptions()
        }

        activateButton.setOnClickListener {
            val method = viewModel.activationMethod.value ?: return@setOnClickListener

            when (method) {
                ActivationMethod.KNOX -> {
                    viewModel.activateViaKnox()
                }
                ActivationMethod.WIRELESS_ADB -> {
                    val code = pairingCodeInput.text.toString().trim()
                    val port = pairingPortInput.text.toString().trim()

                    if (code.isEmpty()) {
                        pairingCodeInput.error = "Enter the 6-digit pairing code"
                        return@setOnClickListener
                    }
                    if (port.isEmpty()) {
                        pairingPortInput.error = "Enter the port number"
                        return@setOnClickListener
                    }

                    viewModel.activateViaWirelessAdb(code, port)
                }
                else -> Toast.makeText(this, "Unsupported method", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showKnoxFlow() {
        inputGroup.isVisible         = false
        openSettingsButton.isVisible = false
        activateButton.text          = "Activate via Knox"
        stepIndicatorText.text       = "Step 1 / 1 — Tap to activate"
    }

    private fun showWirelessAdbFlow() {
        inputGroup.isVisible         = true
        openSettingsButton.isVisible = true
        activateButton.text          = "Activate Device Owner"
        stepIndicatorText.text       = "Enter code & port from the popup, then tap Activate"
    }

    private fun updateInstructions(method: ActivationMethod) {
        instructionsText.text = when (method) {
            ActivationMethod.KNOX -> """
                🎉 Easy Knox Activation (10 seconds)

                1. Tap "Activate via Knox" below
                2. Accept the system prompt
                3. Done!

                ⚠️ Important: Remove all Google / Samsung accounts first
            """.trimIndent()

            ActivationMethod.WIRELESS_ADB -> """
                📱 Wireless ADB Setup (≈ 1 minute)

                1. Open Developer Options (button below)
                2. Turn ON "Wireless Debugging"
                3. Tap "Pair device with pairing code"
                4. A popup appears — copy the 6-digit code AND the port
                5. Paste them into the fields below and tap Activate

                We handle the connection automatically after pairing.

                ⚠️ Critical: Remove ALL accounts first!
                   Settings → Accounts → Remove all
            """.trimIndent()

            else -> "Unknown method"
        }
    }

    private fun openDeveloperOptions() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            Toast.makeText(
                this,
                "Enable Wireless Debugging, tap \"Pair device with pairing code\", " +
                        "then come back and enter the code & port.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Could not open Developer Options. Please navigate there manually.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showLoading(loading: Boolean) {
        progressBar.isVisible        = loading
        activateButton.isEnabled     = !loading
        pairingCodeInput.isEnabled   = !loading
        pairingPortInput.isEnabled   = !loading
        openSettingsButton.isEnabled = !loading
    }

    private fun showSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("🎉 Device Owner Activated!")
            .setMessage(
                "Success! Your device is now under Taqwa protection.\n\n" +
                        "✅ Device Owner active\n" +
                        "✅ System-level control granted\n" +
                        "✅ Uninstall blocked\n\n" +
                        "Tap Continue to proceed."
            )
            .setPositiveButton("Continue") { _, _ ->
                // ✅ FIX: Go to MainActivity — let MainViewModel routing decide the destination.
                // If commitment is already set → Dashboard.
                // If commitment not set yet → CommitmentSelection.
                // This replaces the old hardcoded navigateToCommitmentSelection().
                navigateToMain()
            }
            .setCancelable(false)
            .show()
    }

    private fun showErrorDialog(message: String) {
        val troubleshooting = when {
            message.contains("account", ignoreCase = true) ->
                "\n\n📋 FIX:\n" +
                        "1. Go to Settings → Accounts\n" +
                        "2. Remove ALL accounts (Google, Samsung, Work)\n" +
                        "3. Return here and tap Activate again\n" +
                        "4. You can re-add accounts after activation"

            message.contains("adb", ignoreCase = true) ||
                    message.contains("connection", ignoreCase = true) ||
                    message.contains("pair", ignoreCase = true) ->
                "\n\n📋 FIX:\n" +
                        "1. Make sure Wireless Debugging is still ON\n" +
                        "2. Double-check the 6-digit code (it expires quickly)\n" +
                        "3. Double-check the port number from the popup\n" +
                        "4. Try tapping \"Pair device with pairing code\" again for a fresh code"

            else -> ""
        }

        AlertDialog.Builder(this)
            .setTitle("❌ Activation Failed")
            .setMessage("$message$troubleshooting")
            .setPositiveButton("Try Again", null)
            .setNegativeButton("Get Help") { _, _ ->
                Toast.makeText(this, "Help: support@taqwafortress.com", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    /**
     * Goes to MainActivity and clears the back stack.
     * MainViewModel.resolveRoute() will then decide:
     *   - commitmentEndDate set and valid  → FortressDashboardActivity
     *   - commitmentEndDate null           → CommitmentSelectionActivity
     */
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}