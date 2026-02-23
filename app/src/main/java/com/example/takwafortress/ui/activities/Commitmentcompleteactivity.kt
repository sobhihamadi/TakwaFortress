package com.example.takwafortress.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.takwafortress.R
import com.example.takwafortress.repository.implementations.FirebaseUserRepository
import com.example.takwafortress.services.core.FortressClearService
import com.example.takwafortress.services.core.FortressClearResult
import com.example.takwafortress.ui.viewmodels.AuthViewModel
import com.example.takwafortress.ui.viewmodels.AuthState
import kotlinx.coroutines.launch

class CommitmentCompleteActivity : AppCompatActivity() {

    private lateinit var authViewModel: AuthViewModel
    private lateinit var fortressClearService: FortressClearService

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var contentGroup: View
    private lateinit var tvDaysCompleted: TextView
    private lateinit var tvPlanName: TextView
    private lateinit var btnContinue: Button
    private lateinit var btnViewDashboard: Button
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_commitment_complete)

        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        fortressClearService = FortressClearService(this)

        initViews()
        setupObservers()

        // Clear fortress protections as soon as this screen appears
        clearFortressOnExpiry()
    }

    private fun initViews() {
        progressBar      = findViewById(R.id.progressBar)
        statusText       = findViewById(R.id.statusText)
        contentGroup     = findViewById(R.id.contentGroup)
        tvDaysCompleted  = findViewById(R.id.tvDaysCompleted)
        tvPlanName       = findViewById(R.id.tvPlanName)
        btnContinue      = findViewById(R.id.btnContinue)
        btnViewDashboard = findViewById(R.id.btnViewDashboard)
        btnLogout        = findViewById(R.id.btnLogout)

        // Hide content until fortress is cleared
        contentGroup.visibility = View.GONE
        progressBar.visibility  = View.VISIBLE
        statusText.text         = "Releasing device restrictions..."

        btnContinue.setOnClickListener {
            startActivity(Intent(this, CommitmentSelectionActivity::class.java))
            finish()
        }

        btnViewDashboard.setOnClickListener {
            startActivity(Intent(this, FortressDashboardActivity::class.java))
            finish()
        }

        btnLogout.setOnClickListener {
            authViewModel.signOut()
        }
    }

    private fun setupObservers() {
        authViewModel.authState.observe(this) { state ->
            if (state is AuthState.NotLoggedIn) {
                startActivity(Intent(this, WelcomeActivity::class.java))
                finish()
            }
        }
    }

    /**
     * Clears all fortress restrictions when commitment period ends.
     * Shows the celebration UI only after clearing is done.
     */
    private fun clearFortressOnExpiry() {
        lifecycleScope.launch {
            val result = fortressClearService.clearEverything()

            when (result) {
                is FortressClearResult.Success -> {
                    statusText.text = "✅ All restrictions removed"
                }
                is FortressClearResult.PartialSuccess -> {
                    statusText.text = "⚠️ Some restrictions could not be removed"
                }
                else -> {
                    statusText.text = "✅ Done"
                }
            }

            // Load user stats then show content
            loadStats()

            progressBar.visibility  = View.GONE
            contentGroup.visibility = View.VISIBLE
        }
    }

    private suspend fun loadStats() {
        try {
            val user = FirebaseUserRepository(this).getCurrentUser() ?: return
            tvDaysCompleted.text = "Days completed: ${user.commitmentDays}"
            tvPlanName.text      = "Plan: ${user.selectedPlan}"
        } catch (e: Exception) {
            tvDaysCompleted.text = "Commitment complete!"
            tvPlanName.text      = ""
        }
    }
}