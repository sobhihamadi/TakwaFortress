package com.example.takwafortress.ui.activities

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.takwafortress.R
import com.example.takwafortress.services.filtering.ContentFilteringService
import com.example.takwafortress.services.filtering.ContentFilterResult
import com.example.takwafortress.services.filtering.DnsTestResult
import kotlinx.coroutines.launch

/**
 * Activity for managing content filtering.
 *
 * Allows user to:
 * - Activate 3-layer protection
 * - Test DNS filtering
 * - View protection status
 */
class ContentFilteringActivity : AppCompatActivity() {

    private lateinit var contentFilteringService: ContentFilteringService

    // UI elements
    private lateinit var statusText: TextView
    private lateinit var activateButton: Button
    private lateinit var testDnsButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var testResultCard: LinearLayout
    private lateinit var testResultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_content_filtering)

        contentFilteringService = ContentFilteringService(this)

        initViews()
        setupListeners()
        updateStatus()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        activateButton = findViewById(R.id.activateButton)
        testDnsButton = findViewById(R.id.testDnsButton)
        progressBar = findViewById(R.id.progressBar)
        testResultCard = findViewById(R.id.testResultCard)
        testResultText = findViewById(R.id.testResultText)
    }

    private fun setupListeners() {
        activateButton.setOnClickListener {
            activateProtection()
        }

        testDnsButton.setOnClickListener {
            testDnsFiltering()
        }
    }

    private fun activateProtection() {
        lifecycleScope.launch {
            try {
                showLoading(true, "Activating protection...")

                val result = contentFilteringService.activateFullProtection()

                when (result) {
                    is ContentFilterResult.Success -> {
                        showSuccess("Protection activated!\n\n${result.details}")
                        updateStatus()
                    }

                    is ContentFilterResult.DeviceOwnerRequired -> {
                        showError("Device Owner not active!\n\nPlease activate Device Owner first.")
                    }

                    is ContentFilterResult.Failed -> {
                        showError("Activation failed:\n\n${result.reason}")
                    }
                }

            } catch (e: Exception) {
                showError("Error: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun testDnsFiltering() {
        lifecycleScope.launch {
            try {
                showLoading(true, "Testing DNS filter...")

                // Show the test result card
                testResultCard.isVisible = true

                testResultText.text = "Testing pornhub.com..."
                testResultText.setTextColor(getColor(android.R.color.white))

                val result = contentFilteringService.testDnsFilter()

                when (result) {
                    is DnsTestResult.Success -> {
                        testResultText.text = result.message
                        testResultText.setTextColor(getColor(android.R.color.holo_green_dark))
                    }

                    is DnsTestResult.Failed -> {
                        testResultText.text = result.message
                        testResultText.setTextColor(getColor(android.R.color.holo_red_dark))
                    }

                    is DnsTestResult.Error -> {
                        testResultText.text = "Test error:\n${result.error}"
                        testResultText.setTextColor(getColor(android.R.color.holo_orange_dark))
                    }
                }

            } catch (e: Exception) {
                testResultText.text = "Test crashed: ${e.message}"
                testResultText.setTextColor(getColor(android.R.color.holo_red_dark))
            } finally {
                showLoading(false)
            }
        }
    }

    private fun updateStatus() {
        val statusReport = contentFilteringService.getStatusReport()
        statusText.text = statusReport
    }

    private fun showLoading(loading: Boolean, message: String = "") {
        progressBar.isVisible = loading
        activateButton.isEnabled = !loading
        testDnsButton.isEnabled = !loading

        if (loading && message.isNotEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showError(message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("❌ Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}