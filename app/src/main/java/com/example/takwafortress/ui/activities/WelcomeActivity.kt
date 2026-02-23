package com.example.takwafortress.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.takwafortress.databinding.ActivityWelcomeBinding

/**
 * WelcomeActivity - The landing screen for new users.
 *
 * This is the first screen users see when they're not logged in.
 * Provides options to get started or login.
 *
 * Features:
 * - Eye-catching welcome message
 * - Social proof (10,000+ men in recovery)
 * - Two clear call-to-action buttons
 * - Clean, motivational design
 */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupAnimations()
    }

    /**
     * Sets up UI click listeners and interactions.
     */
    private fun setupUI() {
        binding.apply {
            // Get Started button - navigates to plan selection
            btnGetStarted.setOnClickListener {
                navigateToLogin()
            }

            // Login button - navigates to login screen
            btnLogin.setOnClickListener {
                navigateToLogin()
            }
        }
    }

    /**
     * Sets up entry animations for a polished feel.
     */
    private fun setupAnimations() {
        // Fade in the logo
        binding.ivLogo.alpha = 0f
        binding.ivLogo.animate()
            .alpha(1f)
            .setDuration(800)
            .start()

        // Slide up the title
        binding.tvTitle.translationY = 50f
        binding.tvTitle.alpha = 0f
        binding.tvTitle.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(800)
            .setStartDelay(200)
            .start()

        // Slide up the subtitle
        binding.tvSubtitle.translationY = 50f
        binding.tvSubtitle.alpha = 0f
        binding.tvSubtitle.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(800)
            .setStartDelay(400)
            .start()

        // Fade in buttons
        binding.btnGetStarted.alpha = 0f
        binding.btnGetStarted.animate()
            .alpha(1f)
            .setDuration(800)
            .setStartDelay(600)
            .start()

        binding.btnLogin.alpha = 0f
        binding.btnLogin.animate()
            .alpha(1f)
            .setDuration(800)
            .setStartDelay(700)
            .start()
    }

    /**
     * Navigates to plan selection screen.
     * User will choose their subscription plan before registration.
     */
//    private fun navigateToPlanSelection() {
//        val intent = Intent(this, CommitmentSelectionActivity::class.java)
//        startActivity(intent)
//        // Don't finish() - allow back navigation
//    }

    /**
     * Navigates to login screen for returning users.
     */
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        // Don't finish() - allow back navigation
    }

    /**
     * Disable back button on welcome screen.
     * Users shouldn't be able to go back to MainActivity.
     */
    override fun onBackPressed() {
        // Move task to back instead of finishing
        moveTaskToBack(true)
    }
}