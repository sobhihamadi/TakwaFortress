package com.example.takwafortress.ui.fragments

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.takwafortress.ui.activities.CommitmentSelectionActivity
import com.example.takwafortress.ui.activities.LoginActivity
import com.example.takwafortress.ui.activities.WelcomeActivity
import com.example.takwafortress.repository.implementations.FirebaseUserRepository
import com.example.takwafortress.ui.viewmodels.FortressStatusViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val BG_DARK    = Color.parseColor("#1A1A2E")
private val CARD_BG    = Color.parseColor("#16213E")
private val GREEN      = Color.parseColor("#4CAF50")
private val RED        = Color.parseColor("#F44336")
private val ORANGE     = Color.parseColor("#FF9800")
private val DARK_RED   = Color.parseColor("#7B0000")
private val TEXT_WHITE = Color.WHITE
private val TEXT_GREY  = Color.parseColor("#9E9E9E")

class SettingsFragment : Fragment() {

    private lateinit var viewModel: FortressStatusViewModel
    private lateinit var userRepository: FirebaseUserRepository
    private val auth = FirebaseAuth.getInstance()

    private var startInExpiredMode = false

    private lateinit var normalContent: LinearLayout
    private lateinit var expiredContent: LinearLayout
    private lateinit var switchFactoryReset: Switch
    private lateinit var switchDeveloperMode: Switch
    private lateinit var switchAutoTime: Switch
    private lateinit var switchDns: Switch

    companion object {
        private const val ARG_EXPIRED = "arg_expired"
        private const val TAG = "SettingsFragment"
        fun newInstance(startExpired: Boolean = false) = SettingsFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_EXPIRED, startExpired) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startInExpiredMode = arguments?.getBoolean(ARG_EXPIRED, false) ?: false
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        userRepository = FirebaseUserRepository(requireContext())
        viewModel = ViewModelProvider(requireActivity(), object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return FortressStatusViewModel(requireContext()) as T
            }
        })[FortressStatusViewModel::class.java]

        return buildUi()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        if (startInExpiredMode) applyExpiredMode(true)
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(BG_DARK)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        content.addView(makeLabel("⚙️ Settings", 18f, TEXT_WHITE).also { it.setPadding(0, 0, 0, dp(12)) })

        // ── Normal content (switches) ──
        normalContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        normalContent.addView(makeSettingCard(
            "Block Factory Reset",
            "Prevents factory reset via Settings menu."
        ) { switchFactoryReset = it })

        normalContent.addView(makeSettingCard(
            "Block Developer Mode",
            "Disables USB debugging and ADB access."
        ) { switchDeveloperMode = it })

        normalContent.addView(makeSettingCard(
            "Force Automatic Time",
            "Prevents changing device time to skip countdown."
        ) { switchAutoTime = it })

        normalContent.addView(makeSettingCard(
            "DNS Content Filter",
            "Quick toggle for CleanBrowsing DNS filter."
        ) { switchDns = it })

        content.addView(normalContent)

        // ── Expired content ──
        expiredContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val expiredNotice = makeCard().apply { setBackgroundColor(Color.parseColor("#2A1500")) }
        expiredNotice.addView(makeLabel("⏰ Commitment Period Ended", 16f, ORANGE).also { it.setPadding(0, 0, 0, dp(6)) })
        expiredNotice.addView(makeLabel("Your commitment period has ended. All device protections have been removed. You can start a new plan, logout, or delete your account.", 13f, TEXT_GREY))
        expiredContent.addView(expiredNotice)

        val newPlanCard = makeCard()
        newPlanCard.addView(makeLabel("Ready for a new commitment?", 14f, TEXT_GREY).also { it.setPadding(0, 0, 0, dp(12)) })
        val newPlanBtn = makeButton("🔄 Start New Commitment Plan", GREEN)
        newPlanBtn.setOnClickListener { navigateToNewPlan() }
        newPlanCard.addView(newPlanBtn)
        expiredContent.addView(newPlanCard)

        val logoutCard = makeCard().apply { setBackgroundColor(Color.parseColor("#1A0A0A")) }
        logoutCard.addView(makeLabel("Signed in as: ${auth.currentUser?.email ?: "Unknown"}", 12f, TEXT_GREY).also { it.setPadding(0, 0, 0, dp(12)) })
        val logoutBtn = makeButton("🚪 Logout", RED)
        logoutBtn.setOnClickListener { showLogoutConfirmation() }
        logoutCard.addView(logoutBtn)
        expiredContent.addView(logoutCard)

        val deleteCard = makeCard().apply { setBackgroundColor(Color.parseColor("#1A0000")) }
        deleteCard.addView(makeLabel("Permanently deletes your account and all data. This cannot be undone.", 12f, TEXT_GREY).also { it.setPadding(0, 0, 0, dp(12)) })
        val deleteBtn = makeButton("🗑️ Delete My Account", DARK_RED)
        deleteBtn.setOnClickListener { showDeleteAccountConfirmation() }
        deleteCard.addView(deleteBtn)
        expiredContent.addView(deleteCard)

        content.addView(expiredContent)
        scroll.addView(content)
        return scroll
    }

    /**
     * Helper that builds a settings card with title, subtitle, and a Switch.
     * Uses a callback to give the caller a reference to the created Switch.
     */
    private fun makeSettingCard(title: String, subtitle: String, onSwitch: (Switch) -> Unit): LinearLayout {
        val card = makeCard()
        card.addView(makeLabel(title, 15f, TEXT_WHITE))
        card.addView(makeLabel(subtitle, 12f, TEXT_GREY).also { it.setPadding(0, dp(2), 0, dp(6)) })
        @Suppress("DEPRECATION")
        val sw = Switch(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        onSwitch(sw)
        card.addView(sw)
        return card
    }

    private fun observeViewModel() {
        viewModel.settings.observe(viewLifecycleOwner) { s ->
            // Detach listeners before updating to avoid triggering viewModel calls
            switchFactoryReset.setOnCheckedChangeListener(null)
            switchDeveloperMode.setOnCheckedChangeListener(null)
            switchAutoTime.setOnCheckedChangeListener(null)
            switchDns.setOnCheckedChangeListener(null)

            switchFactoryReset.isChecked  = s.isFactoryResetBlocked
            switchDeveloperMode.isChecked = s.isDeveloperModeBlocked
            switchAutoTime.isChecked      = s.isAutoTimeEnabled
            switchDns.isChecked           = s.isDnsFilterActive

            switchFactoryReset.setOnCheckedChangeListener  { _, c -> viewModel.toggleFactoryResetBlock(c) }
            switchDeveloperMode.setOnCheckedChangeListener { _, c -> viewModel.toggleDeveloperModeBlock(c) }
            switchAutoTime.setOnCheckedChangeListener      { _, c -> viewModel.toggleAutoTime(c) }
            switchDns.setOnCheckedChangeListener           { _, c -> viewModel.toggleDnsFilter(c) }
        }
        viewModel.isCommitmentExpired.observe(viewLifecycleOwner) { expired ->
            if (!startInExpiredMode) applyExpiredMode(expired)
        }
        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg.isNotBlank()) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun applyExpiredMode(expired: Boolean) {
        normalContent.visibility  = if (expired) View.GONE   else View.VISIBLE
        expiredContent.visibility = if (expired) View.VISIBLE else View.GONE
    }

    private fun navigateToNewPlan() {
        startActivity(Intent(requireContext(), CommitmentSelectionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        requireActivity().finish()
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                auth.signOut()
                startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                requireActivity().finish()
            }
            .setNegativeButton("Cancel", null).show()
    }


    private fun showDeleteAccountConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Delete Account")
            .setMessage("This will permanently delete your account and all data.\n\nThis action CANNOT be undone.\n\nAre you absolutely sure?")
            .setPositiveButton("Yes, Delete") { _, _ -> lifecycleScope.launch { deleteAccount() } }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private suspend fun deleteAccount() {
        val progressDialog = ProgressDialog(requireContext()).apply {
            setMessage("Deleting account...")
            setCancelable(false)
            show()
        }
        try {
            // Step 1: Delete Firestore document first (always succeeds)
            val userId = auth.currentUser?.uid
            if (userId != null) {
                userRepository.delete(userId)
                Log.d(TAG, "Firestore document deleted")
            }

            // Step 2: Delete Firebase Auth account
            auth.currentUser?.delete()?.await()
            Log.d(TAG, "Firebase Auth account deleted")

            progressDialog.dismiss()
            navigateAfterDeletion()

        } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
            // Firebase requires re-authentication before deleting an account
            // if the user hasn't signed in recently. We need to re-auth and retry.
            progressDialog.dismiss()
            Log.d(TAG, "Re-auth required for account deletion")
            promptReAuthAndDelete()

        } catch (e: Exception) {
            progressDialog.dismiss()
            Log.e(TAG, "Delete account error: ${e.message}")
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Shows a password dialog, re-authenticates, then retries deletion.
     * This properly removes the Firebase Auth account so the email is freed.
     */
    private fun promptReAuthAndDelete() {
        val isGoogleUser = auth.currentUser?.providerData
            ?.any { it.providerId == "google.com" } ?: false

        if (isGoogleUser) {
            // Google users: we can't silently re-auth without launching the Google
            // sign-in flow again, which is complex UX. Instead, sign out and tell
            // the user to log back in and try again.
            // NOTE: Firestore document is already deleted at this point.
            AlertDialog.Builder(requireContext())
                .setTitle("One More Step")
                .setMessage("For security, please sign in again to confirm account deletion.\n\nYour data has already been removed.")
                .setPositiveButton("Sign In Again") { _, _ ->
                    auth.signOut()
                    startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        // Pass a flag so LoginActivity knows to delete Auth after login
                        putExtra("PENDING_ACCOUNT_DELETE", true)
                    })
                    requireActivity().finish()
                }
                .setCancelable(false)
                .show()
        } else {
            // Email/password users: ask for password, re-auth, retry deletion
            val passwordInput = EditText(requireContext()).apply {
                hint = "Enter your password to confirm"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Confirm Your Password")
                .setMessage("For security, please enter your password to complete account deletion.")
                .setView(passwordInput)
                .setPositiveButton("Delete My Account") { _, _ ->
                    val password = passwordInput.text.toString()
                    if (password.isBlank()) {
                        Toast.makeText(requireContext(), "Password cannot be empty", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    lifecycleScope.launch { reAuthAndDelete(password) }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun reAuthAndDelete(password: String) {
        val progressDialog = ProgressDialog(requireContext()).apply {
            setMessage("Confirming deletion...")
            setCancelable(false)
            show()
        }
        try {
            val user  = auth.currentUser ?: throw Exception("No user signed in")
            val email = user.email       ?: throw Exception("Could not get user email")

            // Re-authenticate with fresh credential
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()
            Log.d(TAG, "Re-authentication successful")

            // Now delete the Auth account — this will succeed
            user.delete().await()
            Log.d(TAG, "Firebase Auth account deleted after re-auth")

            progressDialog.dismiss()
            navigateAfterDeletion()

        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            progressDialog.dismiss()
            Toast.makeText(requireContext(), "❌ Incorrect password. Please try again.", Toast.LENGTH_LONG).show()
            promptReAuthAndDelete() // Let them try again
        } catch (e: Exception) {
            progressDialog.dismiss()
            Log.e(TAG, "Re-auth delete failed: ${e.message}")
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun navigateAfterDeletion() {
        Toast.makeText(requireContext(), "✅ Account deleted successfully", Toast.LENGTH_SHORT).show()
        startActivity(Intent(requireContext(), WelcomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        requireActivity().finish()
    }

    private fun makeCard() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(CARD_BG); setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(10) }
    }
    private fun makeLabel(text: String, size: Float, color: Int, gravity: Int = Gravity.START) =
        TextView(requireContext()).apply { this.text = text; textSize = size; setTextColor(color); this.gravity = gravity }
    private fun makeButton(label: String, color: Int) = Button(requireContext()).apply {
        text = label; textSize = 15f; setTextColor(TEXT_WHITE); setBackgroundColor(color)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}