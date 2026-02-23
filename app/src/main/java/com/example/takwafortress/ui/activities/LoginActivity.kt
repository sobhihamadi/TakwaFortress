package com.example.takwafortress.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.takwafortress.databinding.ActivityLoginBinding
import com.example.takwafortress.auth.GoogleSignInHelper
import com.example.takwafortress.ui.viewmodels.AuthViewModel
import com.example.takwafortress.ui.viewmodels.AuthState
import com.example.takwafortress.util.constants.AppConstants

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: AuthViewModel
    private lateinit var googleSignInHelper: GoogleSignInHelper

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleGoogleSignInResult(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            btnLogin.setOnClickListener { loginWithEmail() }
            btnForgotPassword.setOnClickListener { handleForgotPassword() }
            btnSignUpLink.setOnClickListener { navigateToRegistration() }
        }
    }

    private fun observeViewModel() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Loading -> showLoading(true)

                is AuthState.Success -> {
                    showLoading(false)
                    // ✅ FIX: Go to MainActivity — let MainViewModel routing decide the destination.
                    // MainViewModel checks: subscriptionStatus, hasDeviceOwner, commitmentEndDate
                    // and routes to the correct screen automatically.
                    navigateToMain()
                }

                is AuthState.PasswordResetSent -> {
                    showLoading(false)
                    Toast.makeText(
                        this,
                        "Password reset email sent. Check your inbox.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                is AuthState.Error -> {
                    showLoading(false)
                    showError(state.message)
                }

                else -> showLoading(false)
            }
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInHelper.getSignInIntent()
        googleSignInLauncher.launch(signInIntent)
    }

    private fun handleGoogleSignInResult(data: Intent?) {
        val tokenResult = googleSignInHelper.handleSignInResult(data)
        tokenResult.fold(
            onSuccess = { idToken -> viewModel.signInWithGoogle(idToken, "") },
            onFailure = { error -> showError("Google sign-in failed: ${error.message}") }
        )
    }

    private fun loginWithEmail() {
        val email    = binding.emailInput.text.toString().trim()
        val password = binding.etPassword.text.toString()

        val emailError = viewModel.validateEmail(email)
        if (emailError != null) {
            binding.emailInput.error = emailError
            return
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "Password is required"
            return
        }

        viewModel.loginWithEmail(email, password)
    }

    private fun handleForgotPassword() {
        val email = binding.emailInput.text.toString().trim()
        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your email address", Toast.LENGTH_SHORT).show()
            return
        }
        val emailError = viewModel.validateEmail(email)
        if (emailError != null) {
            Toast.makeText(this, emailError, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.sendPasswordResetEmail(email)
    }

    private fun navigateToRegistration() {
        startActivity(Intent(this, RegistrationActivity::class.java))
        finish()
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility  = if (show) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled      = !show
        binding.btnGoogleSignIn.isEnabled = !show
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(AppConstants.LOG_TAG, "Login error: $message")
    }

    /**
     * Goes to MainActivity and clears the back stack.
     * MainViewModel.resolveRoute() checks the user's Firestore document and decides:
     *
     *   subscriptionStatus = PENDING                   → CommitmentSelectionActivity
     *   subscriptionStatus = TRIAL/ACTIVE
     *     + hasDeviceOwner = false                     → DeviceOwnerSetupActivity
     *     + hasDeviceOwner = true
     *       + commitmentEndDate null                   → CommitmentSelectionActivity
     *       + commitmentEndDate valid (future)         → FortressDashboardActivity
     *       + commitmentEndDate expired                → CommitmentCompleteActivity
     *   subscriptionStatus = EXPIRED/CANCELLED         → SubscriptionExpiredActivity
     */
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}