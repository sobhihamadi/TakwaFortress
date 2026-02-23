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
import com.example.takwafortress.services.core.FortressClearService
import com.example.takwafortress.ui.viewmodels.AuthViewModel
import com.example.takwafortress.ui.viewmodels.AuthState
import kotlinx.coroutines.launch

class TrialExpiredActivity : AppCompatActivity() {

    private lateinit var authViewModel: AuthViewModel

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var contentGroup: View
    private lateinit var btnViewPlans: Button
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trial_expired)

        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        initViews()
        setupObservers()
        clearFortressOnExpiry()
    }

    private fun initViews() {
        progressBar  = findViewById(R.id.progressBar)
        statusText   = findViewById(R.id.statusText)
        contentGroup = findViewById(R.id.contentGroup)
        btnViewPlans = findViewById(R.id.btnViewPlans)
        btnLogout    = findViewById(R.id.btnLogout)

        contentGroup.visibility = View.GONE
        progressBar.visibility  = View.VISIBLE
        statusText.text         = "Releasing device restrictions..."

        btnViewPlans.setOnClickListener {
            startActivity(Intent(this, CommitmentSelectionActivity::class.java))
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

    private fun clearFortressOnExpiry() {
        lifecycleScope.launch {
            FortressClearService(this@TrialExpiredActivity).clearEverything()
            progressBar.visibility  = View.GONE
            contentGroup.visibility = View.VISIBLE
        }
    }
}