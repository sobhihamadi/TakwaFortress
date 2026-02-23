package com.example.takwafortress.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.takwafortress.databinding.ActivityRegistrationBinding
import com.example.takwafortress.auth.GoogleSignInHelper
import com.example.takwafortress.ui.viewmodels.AuthViewModel
import com.example.takwafortress.ui.viewmodels.AuthState
import com.example.takwafortress.util.constants.AppConstants

class RegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistrationBinding
    private lateinit var viewModel: AuthViewModel
    private lateinit var googleSignInHelper: GoogleSignInHelper

    private var selectedPlan: String = "free_trial"

    // Hold these so the verification buttons can reference them
    private var pendingUserId: String = ""
    private var pendingEmail: String  = ""
    private var pendingPlan: String   = ""

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleGoogleSignInResult(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedPlan = intent.getStringExtra("SELECTED_PLAN") ?: "free_trial"
        Log.d(AppConstants.LOG_TAG, "Selected plan: $selectedPlan")

        initializeComponents()
        setupUI()
        observeViewModel()
    }

    private fun initializeComponents() {
        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        googleSignInHelper = GoogleSignInHelper(this)
    }

    private fun setupUI() {
        binding.apply {
            btnGoogleSignIn.setOnClickListener { signInWithGoogle() }
            btnCreateAccount.setOnClickListener { registerWithEmail() }
            btnLoginLink.setOnClickListener { navigateToLogin() }

            // These buttons live in the verification waiting view (emailVerificationGroup).
            // They're hidden initially and shown after the email is sent.
            btnIveVerified.setOnClickListener {
                viewModel.checkEmailVerificationAndComplete(
                    pendingUserId, pendingEmail, pendingPlan
                )
            }
            btnResendEmail.setOnClickListener {
                viewModel.resendVerificationEmail()
            }
            btnBackToRegister.setOnClickListener {
                showRegistrationForm()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Loading -> {
                    showLoading(true)
                }

                // ── Email/password path: verification required ──────────────
                is AuthState.AwaitingEmailVerification -> {
                    showLoading(false)
                    pendingUserId = state.userId
                    pendingEmail  = state.email
                    pendingPlan   = state.selectedPlan
                    showVerificationWaitingScreen(state.email)
                }

                // User tapped "I've verified" but Firebase says not yet
                is AuthState.EmailNotYetVerified -> {
                    showLoading(false)
                    pendingUserId = state.userId
                    pendingEmail  = state.email
                    pendingPlan   = state.selectedPlan
                    Toast.makeText(
                        this,
                        "Email not verified yet. Please check your inbox and tap the link, then try again.",
                        Toast.LENGTH_LONG
                    ).show()
                    // Stay on the verification screen
                    showVerificationWaitingScreen(state.email, alreadyShowing = true)
                }

                is AuthState.VerificationEmailResent -> {
                    showLoading(false)
                    Toast.makeText(
                        this,
                        "Verification email resent to $pendingEmail",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // ── Success (email verified + Firestore doc created) ─────────
                is AuthState.Success -> {
                    showLoading(false)
                    handleRegistrationSuccess()
                }

                is AuthState.Error -> {
                    showLoading(false)
                    showError(state.message)
                }

                else -> {
                    showLoading(false)
                }
            }
        }
    }

    // ── UI state helpers ──────────────────────────────────────────────────────

    /**
     * Hides the registration form and shows the "check your inbox" panel.
     * Called once the verification email has been sent.
     *
     * [alreadyShowing] = true skips the panel swap animation when we're
     * simply updating the UI because the user re-tapped "I've verified".
     */
    private fun showVerificationWaitingScreen(email: String, alreadyShowing: Boolean = false) {
        if (!alreadyShowing) {
            binding.registrationFormGroup.visibility  = View.GONE
            binding.emailVerificationGroup.visibility = View.VISIBLE
        }
        binding.tvVerificationMessage.text =
            "We sent a verification link to:\n\n$email\n\n" +
                    "Open your email, tap the link, then come back and tap " +
                    "\"I've Verified My Email\" below."
    }

    private fun showRegistrationForm() {
        binding.emailVerificationGroup.visibility = View.GONE
        binding.registrationFormGroup.visibility  = View.VISIBLE
    }

    // ── Auth actions ──────────────────────────────────────────────────────────

    private fun signInWithGoogle() {
        val signInIntent = googleSignInHelper.getSignInIntent()
        googleSignInLauncher.launch(signInIntent)
    }

    private fun handleGoogleSignInResult(data: android.content.Intent?) {
        val tokenResult = googleSignInHelper.handleSignInResult(data)
        tokenResult.fold(
            onSuccess = { idToken -> viewModel.signInWithGoogle(idToken, selectedPlan) },
            onFailure = { error  -> showError("Google sign-in failed: ${error.message}") }
        )
    }

    private fun registerWithEmail() {
        val email           = binding.emailInput.text.toString().trim()
        val password        = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        val emailError = viewModel.validateEmail(email)
        if (emailError != null) {
            binding.emailInput.error = emailError
            return
        }

        val passwordError = viewModel.validatePassword(password)
        if (passwordError != null) {
            binding.etPassword.error = passwordError
            return
        }

        val confirmError = viewModel.validatePasswordConfirmation(password, confirmPassword)
        if (confirmError != null) {
            binding.etConfirmPassword.error = confirmError
            return
        }

        if (!binding.cbAgreeTerms.isChecked) {
            showError("Please agree to Terms & Privacy Policy")
            return
        }

        viewModel.registerWithEmail(email, password, selectedPlan)
    }

    private fun handleRegistrationSuccess() {
        Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, CommitmentSelectionActivity::class.java)
        intent.putExtra("SELECTED_PLAN", selectedPlan)
        startActivity(intent)
        finish()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility        = if (show) View.VISIBLE else View.GONE
        binding.btnCreateAccount.isEnabled    = !show
        binding.btnGoogleSignIn.isEnabled     = !show
        binding.btnIveVerified.isEnabled      = !show
        binding.btnResendEmail.isEnabled      = !show
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(AppConstants.LOG_TAG, "Registration error: $message")
    }
}