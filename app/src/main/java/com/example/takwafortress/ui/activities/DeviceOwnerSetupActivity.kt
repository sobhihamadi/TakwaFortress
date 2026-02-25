package com.example.takwafortress.ui.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.example.takwafortress.R
import com.example.takwafortress.model.enums.ActivationMethod
import com.example.takwafortress.services.core.DeviceOwnerService
import com.example.takwafortress.services.security.NotificationReplyReceiver
import com.example.takwafortress.ui.viewmodels.DeviceOwnerSetupViewModel
import com.example.takwafortress.ui.viewmodels.DeviceOwnerSetupState

class DeviceOwnerSetupActivity : AppCompatActivity() {

    private lateinit var viewModel: DeviceOwnerSetupViewModel
    private lateinit var deviceOwnerService: DeviceOwnerService

    private lateinit var deviceBrandText      : TextView
    private lateinit var activationMethodText : TextView
    private lateinit var instructionsText     : TextView
    private lateinit var stepIndicatorText    : TextView
    private lateinit var openSettingsButton   : Button
    private lateinit var activateButton       : Button
    private lateinit var progressBar          : ProgressBar
    private lateinit var statusText           : TextView

    // Prevents showing the success dialog twice if both onResume and the observer fire
    private var successHandled = false

    private val adbResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(NotificationReplyReceiver.EXTRA_SUCCESS, false)
            val error   = intent.getStringExtra(NotificationReplyReceiver.EXTRA_ERROR_MESSAGE) ?: ""
            viewModel.receiveAdbResult(success, error)
        }
    }

    // ── Notification permission launcher ─────────────────────────────────────
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Permission granted — start discovery normally
                viewModel.startDiscovery()
            } else {
                // User denied — show rationale explaining why it's critical
                showNotificationPermissionRationaleDialog()
            }
        }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_owner_setup)

        viewModel = ViewModelProvider(this)[DeviceOwnerSetupViewModel::class.java]
        deviceOwnerService = DeviceOwnerService(this)

        initViews()
        setupObservers()
        setupListeners()

        viewModel.checkDeviceOwnerStatus()

        val filter = IntentFilter(NotificationReplyReceiver.ACTION_ADB_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(adbResultReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(adbResultReceiver, filter)
        }

        // ✅ Request notification permission before starting discovery
        checkAndRequestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()

        // ✅ THE KEY FIX:
        // The ADB command runs in the background while the user is in Settings.
        // LiveData observers are PAUSED while the Activity is stopped/in background,
        // so the Success state posts but never reaches the UI.
        // When the user comes back (onResume), we directly check DevicePolicyManager —
        // the ground truth — and show the dialog immediately if Device Owner is active.
        if (!successHandled && deviceOwnerService.isDeviceOwner()) {
            successHandled = true
            viewModel.stopDiscovery()
            showSuccessDialog()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(adbResultReceiver) }
        viewModel.stopDiscovery()
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
                is DeviceOwnerSetupState.Ready -> {
                    showLoading(false)
                    updateInstructions(state.method)
                }
                is DeviceOwnerSetupState.WaitingForPairing -> {
                    showLoading(false)
                    instructionsText.text  = state.instructions
                    stepIndicatorText.text = "⏳ Waiting for Wireless Debugging popup…"
                    statusText.text        = "Open Settings → Wireless Debugging → Pair with code"
                }
                is DeviceOwnerSetupState.Activating -> {
                    showLoading(true)
                    statusText.text = state.message
                }
                is DeviceOwnerSetupState.Success -> {
                    // Fires if Activity happened to be in foreground when result arrived
                    if (!successHandled) {
                        successHandled = true
                        showLoading(false)
                        viewModel.stopDiscovery()
                        showSuccessDialog()
                    }
                }
                is DeviceOwnerSetupState.AlreadyActive -> {
                    showLoading(false)
                    navigateToMain()
                }
                is DeviceOwnerSetupState.Error -> {
                    showLoading(false)
                    showErrorDialog(state.message)
                    viewModel.startDiscovery()
                }
            }
        }
    }

    private fun setupListeners() {
        openSettingsButton.setOnClickListener { openDeveloperOptions() }
        activateButton.setOnClickListener {
            if (viewModel.activationMethod.value == ActivationMethod.KNOX) {
                viewModel.activateViaKnox()
            }
        }
    }

    private fun showKnoxFlow() {
        openSettingsButton.isVisible = false
        activateButton.isVisible     = true
        activateButton.text          = "Activate via Knox"
        stepIndicatorText.text       = "Step 1 / 1 — Tap to activate"
    }

    private fun showWirelessAdbFlow() {
        openSettingsButton.isVisible = true
        activateButton.isVisible     = false
        stepIndicatorText.text       = "Tap the button below to open Developer Options"
    }

    private fun updateInstructions(method: ActivationMethod) {
        instructionsText.text = when (method) {
            ActivationMethod.KNOX -> """
                🎉 Easy Knox Activation

                1. Tap "Activate via Knox" below
                2. Accept the system prompt
                3. Done!

                ⚠️ Remove all Google / Samsung accounts first.
            """.trimIndent()

            ActivationMethod.WIRELESS_ADB -> """
                📱 Wireless ADB — Automatic Setup

                ⚠️ FIRST: Remove ALL accounts
                   Settings → Accounts → Remove all

                Then:
                1. Tap "Open Developer Options" below
                2. Turn ON "Wireless Debugging"
                3. Tap "Pair device with pairing code"
                4. Pull down notifications — enter the 6-digit code there
                5. Done! Port is detected automatically.
            """.trimIndent()

            else -> ""
        }
    }

    private fun openDeveloperOptions() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            Toast.makeText(
                this,
                "Enable Wireless Debugging, then tap \"Pair device with pairing code\".",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Please navigate to Developer Options manually.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showLoading(loading: Boolean) {
        progressBar.isVisible        = loading
        openSettingsButton.isEnabled = !loading
        activateButton.isEnabled     = !loading
    }

    private fun showSuccessDialog() {
        // Also save to Firestore in case the background broadcast path missed it
        viewModel.saveDeviceOwnerToFirestore()

        AlertDialog.Builder(this)
            .setTitle("🎉 Device Owner Activated!")
            .setMessage(
                "Success! Your device is now under Taqwa protection.\n\n" +
                        "✅ Device Owner active\n" +
                        "✅ System-level control granted\n" +
                        "✅ Uninstall blocked\n\n" +
                        "Tap Continue to proceed."
            )
            .setPositiveButton("Continue") { _, _ -> navigateToMain() }
            .setCancelable(false)
            .show()
    }

    private fun showErrorDialog(message: String) {
        val troubleshooting = when {
            message.contains("account", ignoreCase = true) ->
                "\n\n📋 FIX:\n1. Settings → Accounts\n2. Remove ALL accounts\n3. Come back — notification will reappear automatically"
            message.contains("pair",  ignoreCase = true) ||
                    message.contains("code",  ignoreCase = true) ||
                    message.contains("port",  ignoreCase = true) ->
                "\n\n📋 FIX:\n1. Close and re-open \"Pair with code\" in Settings\n2. A new notification will appear automatically\n3. Enter the NEW 6-digit code"
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

    private fun navigateToMain() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    // ── Notification Permission ───────────────────────────────────────────────

    /**
     * Entry point — checks current permission state and acts accordingly.
     * On Android < 13 notifications are granted at install, so we skip straight
     * to startDiscovery().
     */
    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                // ✅ Already granted — proceed immediately
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    viewModel.startDiscovery()
                }

                // ⚠️ User denied before — show our explanation first, then re-ask
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    AlertDialog.Builder(this)
                        .setTitle("🔔 Notifications Required")
                        .setMessage(
                            "Taqwa Fortress needs notification access to:\n\n" +
                                    "• Show the ADB pairing code prompt\n" +
                                    "• Alert you when device protection is ready\n\n" +
                                    "Without this, the automatic setup cannot work."
                        )
                        .setPositiveButton("Allow") { _, _ ->
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("Not Now") { _, _ ->
                            // Still try to proceed but warn the user
                            showNotificationPermissionRationaleDialog()
                        }
                        .setCancelable(false)
                        .show()
                }

                // 🆕 First time — show system permission popup directly
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Android 12 and below — permission granted automatically at install
            viewModel.startDiscovery()
        }
    }

    /**
     * Shown when the user has permanently denied notifications.
     * Gives them a path to fix it via App Settings, or lets them continue
     * anyway (discovery will run but the pairing notification won't appear).
     */
    private fun showNotificationPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Notifications Blocked")
            .setMessage(
                "Without notifications, the ADB pairing prompt won't appear " +
                        "and the automatic setup will not work.\n\n" +
                        "To fix this, go to:\n" +
                        "Settings → Apps → Taqwa Fortress → Notifications → Enable"
            )
            .setPositiveButton("Open App Settings") { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                )
                // Start discovery anyway — user may enable it in settings and come back
                viewModel.startDiscovery()
            }
            .setNegativeButton("Continue Anyway") { _, _ ->
                // Discovery runs but notification won't show — user is aware
                viewModel.startDiscovery()
            }
            .setCancelable(false)
            .show()
    }
}