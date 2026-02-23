package com.example.takwafortress.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.example.takwafortress.R
import com.example.takwafortress.ui.viewmodels.LicenseActivationViewModel
import com.example.takwafortress.ui.viewmodels.LicenseActivationState

/**
 * License Activation Activity - User enters email and license key.
 */
class LicenseActivationActivity : AppCompatActivity() {

    private lateinit var viewModel: LicenseActivationViewModel

    // UI Elements (you'll create these in XML)
    private lateinit var emailInput: EditText
    private lateinit var licenseKeyInput: EditText
    private lateinit var activateButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var hardwareIdText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license_activation)

        viewModel = ViewModelProvider(this)[LicenseActivationViewModel::class.java]

        initViews()
        setupObservers()
        setupListeners()
    }

    /**
     * Initializes views (bind from XML).
     */
    private fun initViews() {
        emailInput = findViewById(R.id.emailInput)
        licenseKeyInput = findViewById(R.id.licenseKeyInput)
        activateButton = findViewById(R.id.activateButton)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        hardwareIdText = findViewById(R.id.hardwareIdText)
    }

    /**
     * Sets up ViewModel observers.
     */
    private fun setupObservers() {
        // Observe hardware ID
        viewModel.hardwareId.observe(this) { hwid ->
            hardwareIdText.text = "Device ID: ${hwid.take(16)}..."
        }

        // Observe activation state
        viewModel.activationState.observe(this) { state ->
            when (state) {
                is LicenseActivationState.Loading -> {
                    showLoading(true)
                    statusText.text = "Validating license..."
                }
                is LicenseActivationState.Success -> {
                    showLoading(false)
                    statusText.text = "✅ License activated successfully!"
                    Toast.makeText(this, "Welcome to Taqwa Fortress!", Toast.LENGTH_LONG).show()
                    navigateToDeviceOwnerSetup()
                }
                is LicenseActivationState.Error -> {
                    showLoading(false)
                    statusText.text = "❌ ${state.message}"
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                else -> {
                    showLoading(false)
                }
            }
        }
    }

    /**
     * Sets up button listeners.
     */
    private fun setupListeners() {
        activateButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val licenseKey = licenseKeyInput.text.toString().trim().uppercase()

            // Validate inputs
            if (!viewModel.validateEmailFormat(email)) {
                emailInput.error = "Invalid email format"
                return@setOnClickListener
            }

            if (!viewModel.validateLicenseKeyFormat(licenseKey)) {
                licenseKeyInput.error = "Invalid license key format (TAQWA-XXXX-XXXX-XXXX)"
                return@setOnClickListener
            }

            // Activate license
            viewModel.activateLicense(email, licenseKey)
        }
    }

    /**
     * Shows/hides loading state.
     */
    private fun showLoading(loading: Boolean) {
        progressBar.isVisible = loading
        activateButton.isEnabled = !loading
        emailInput.isEnabled = !loading
        licenseKeyInput.isEnabled = !loading
    }

    /**
     * Navigates to Device Owner Setup.
     */
    private fun navigateToDeviceOwnerSetup() {
        val intent = Intent(this, DeviceOwnerSetupActivity::class.java)
        startActivity(intent)
        finish()
    }
}