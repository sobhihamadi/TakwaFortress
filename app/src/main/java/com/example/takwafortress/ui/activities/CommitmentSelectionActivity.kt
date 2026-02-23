package com.example.takwafortress.ui.activities

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.example.takwafortress.R
import com.example.takwafortress.model.enums.CommitmentPlan
import com.example.takwafortress.ui.viewmodels.CommitmentSelectionViewModel
import com.example.takwafortress.ui.viewmodels.CommitmentSelectionState

class CommitmentSelectionActivity : AppCompatActivity() {

    private lateinit var viewModel: CommitmentSelectionViewModel

    private lateinit var planRadioGroup: RadioGroup
    private lateinit var planTrialRadio: RadioButton
    private lateinit var planMonthlyRadio: RadioButton
    private lateinit var planQuarterlyRadio: RadioButton
    private lateinit var planBiannualRadio: RadioButton
    private lateinit var planAnnualRadio: RadioButton
    private lateinit var planDetailsText: TextView
    private lateinit var proceedButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private lateinit var webViewContainer: LinearLayout
    private lateinit var checkoutWebView: WebView
    private lateinit var btnCloseWebView: Button

    private var pendingPlan: CommitmentPlan? = null
    private var paymentHandled = false

    // Must match exactly what you set in Whop dashboard → plan → "Redirect after checkout"
    private val SUCCESS_REDIRECT_URL = "https://taqwafortress.app/payment-success"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_commitment_selection)

        viewModel = ViewModelProvider(this)[CommitmentSelectionViewModel::class.java]

        initViews()
        setupWebView()
        setupObservers()
        setupListeners()
    }

    private fun initViews() {
        planRadioGroup      = findViewById(R.id.planRadioGroup)
        planTrialRadio      = findViewById(R.id.planTrialRadio)
        planMonthlyRadio    = findViewById(R.id.planMonthlyRadio)
        planQuarterlyRadio  = findViewById(R.id.planQuarterlyRadio)
        planBiannualRadio   = findViewById(R.id.planBiannualRadio)
        planAnnualRadio     = findViewById(R.id.planAnnualRadio)
        planDetailsText     = findViewById(R.id.planDetailsText)
        proceedButton       = findViewById(R.id.activateButton)
        progressBar         = findViewById(R.id.progressBar)
        statusText          = findViewById(R.id.statusText)
        webViewContainer    = findViewById(R.id.webViewContainer)
        checkoutWebView     = findViewById(R.id.checkoutWebView)
        btnCloseWebView     = findViewById(R.id.btnCloseWebView)

        planTrialRadio.text     = "3-Day Free Trial — FREE"
        planMonthlyRadio.text   = "1 Month — $3.90"
        planQuarterlyRadio.text = "3 Months — $9.90"
        planBiannualRadio.text  = "6 Months — $15.90"
        planAnnualRadio.text    = "1 Year — $24.90"
    }

    // ---------------------------------------------------------------
    // WebView — intercept success URL in BOTH callbacks for reliability
    // ---------------------------------------------------------------
    private fun setupWebView() {
        checkoutWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        checkoutWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                if (request.url.toString().startsWith(SUCCESS_REDIRECT_URL)) {
                    handlePaymentSuccess()
                    return true
                }
                return false
            }

            // Safety net — fires on ALL Android versions
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url.startsWith(SUCCESS_REDIRECT_URL)) {
                    view.stopLoading()
                    handlePaymentSuccess()
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Observers
    // ---------------------------------------------------------------
    private fun setupObservers() {
        viewModel.selectedPlan.observe(this) { plan ->
            planDetailsText.text = viewModel.getPlanDetails(plan)
            proceedButton.text   = if (plan.isFree) "Start Free Trial" else "Pay ${plan.getPriceDisplay()}"
        }

        viewModel.selectionState.observe(this) { state ->
            when (state) {
                is CommitmentSelectionState.Ready -> {
                    showLoading(false)
                    statusText.text = "Choose your plan"
                }
                is CommitmentSelectionState.OpenWhopCheckout -> {
                    showLoading(false)
                    openWhopCheckout(state.plan)
                }
                is CommitmentSelectionState.Saving -> {
                    // Writing subscription to Firestore after payment
                    showLoading(true)
                    statusText.text = "Confirming your plan..."
                }
                is CommitmentSelectionState.PaymentSuccess -> {
                    showLoading(false)
                    statusText.text = "✅ Plan activated!"
                    navigateToDeviceOwnerSetup(state.plan)
                }
                is CommitmentSelectionState.Error -> {
                    showLoading(false)
                    statusText.text = "❌ ${state.message}"
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Listeners
    // ---------------------------------------------------------------
    private fun setupListeners() {
        planRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val plan = when (checkedId) {
                R.id.planTrialRadio     -> CommitmentPlan.TRIAL_3
                R.id.planMonthlyRadio   -> CommitmentPlan.MONTHLY
                R.id.planQuarterlyRadio -> CommitmentPlan.QUARTERLY
                R.id.planBiannualRadio  -> CommitmentPlan.BIANNUAL
                R.id.planAnnualRadio    -> CommitmentPlan.ANNUAL
                else -> return@setOnCheckedChangeListener
            }
            viewModel.selectPlan(plan)
        }

        proceedButton.setOnClickListener {
            val plan = viewModel.selectedPlan.value ?: run {
                Toast.makeText(this, "Please select a plan first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showConfirmationDialog(plan)
        }

        btnCloseWebView.setOnClickListener {
            showCancelConfirmationDialog()
        }
    }

    // ---------------------------------------------------------------
    // Payment success handler
    // ---------------------------------------------------------------
    private fun handlePaymentSuccess() {
        if (paymentHandled) return
        paymentHandled = true

        val plan = pendingPlan
        hideWebView()

        if (plan != null) {
            // This saves ACTIVE status to Firestore then emits PaymentSuccess
            viewModel.onPaymentConfirmed(plan)
        } else {
            Toast.makeText(this, "Payment confirmed but plan lost — please try again.", Toast.LENGTH_LONG).show()
            viewModel.onPaymentCancelled()
        }
    }

    // ---------------------------------------------------------------
    // WebView helpers
    // ---------------------------------------------------------------
    private fun openWhopCheckout(plan: CommitmentPlan) {
        paymentHandled = false
        pendingPlan = plan
        webViewContainer.isVisible = true
        checkoutWebView.loadUrl(plan.whopCheckoutUrl)
    }

    private fun hideWebView() {
        webViewContainer.isVisible = false
        checkoutWebView.stopLoading()
        pendingPlan = null
    }

    private fun showCancelConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Payment?")
            .setMessage("Are you sure? Your payment was not completed.")
            .setPositiveButton("Yes, go back") { _, _ ->
                hideWebView()
                viewModel.onPaymentCancelled()
            }
            .setNegativeButton("Continue paying", null)
            .show()
    }

    private fun showConfirmationDialog(plan: CommitmentPlan) {
        val message = if (plan.isFree) {
            "You are starting a 3-day FREE trial. No payment needed."
        } else {
            "You will be taken to a secure Whop payment page to pay ${plan.getPriceDisplay()} for ${plan.displayName}."
        }

        AlertDialog.Builder(this)
            .setTitle("Confirm Plan")
            .setMessage(message)
            .setPositiveButton("Confirm") { _, _ -> viewModel.proceedWithPlan() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------
    // Navigation — passes plan name so DeviceOwnerSetupActivity knows
    // ---------------------------------------------------------------
    private fun navigateToDeviceOwnerSetup(plan: CommitmentPlan) {
        val intent = Intent(this, DeviceOwnerSetupActivity::class.java)
        intent.putExtra("COMMITMENT_PLAN", plan.name)
        startActivity(intent)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webViewContainer.isVisible) {
            showCancelConfirmationDialog()
        } else {
            super.onBackPressed()
        }
    }

    private fun showLoading(loading: Boolean) {
        progressBar.isVisible    = loading
        proceedButton.isEnabled  = !loading
        planRadioGroup.isEnabled = !loading
    }
}