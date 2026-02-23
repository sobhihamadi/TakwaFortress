package com.example.takwafortress.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.takwafortress.R
import com.example.takwafortress.model.builders.IdentifierUserBuilder
import com.example.takwafortress.model.builders.UserBuilder
import com.example.takwafortress.repository.implementations.FirebaseUserRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class UnauthorizedDeviceActivity : AppCompatActivity() {

    private val userRepository by lazy { FirebaseUserRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unauthorized_device)

        val currentDeviceId = intent.getStringExtra("CURRENT_DEVICE_ID") ?: ""

        findViewById<Button>(R.id.btnTransfer).setOnClickListener {
            showTransferConfirmation(currentDeviceId)
        }

        // ✅ FIXED: Cancel → go to LoginActivity instead of closing the app
        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            FirebaseAuth.getInstance().signOut()   // clear the Firebase session
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun showTransferConfirmation(currentDeviceId: String) {
        AlertDialog.Builder(this)
            .setTitle("Transfer to This Device?")
            .setMessage(
                "This will transfer your account to this device.\n\n" +
                        "⚠️ The previous device will be locked out immediately.\n\n" +
                        "Are you sure?"
            )
            .setPositiveButton("Yes, Transfer") { _, _ ->
                transferToCurrentDevice(currentDeviceId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun transferToCurrentDevice(currentDeviceId: String) {
        lifecycleScope.launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                    ?: throw Exception("No authenticated user")

                val existingUser = userRepository.get(userId)

                val updatedUser = UserBuilder.newBuilder()
                    .setEmail(existingUser.email)
                    .setDeviceId(currentDeviceId)
                    .setSubscriptionStatus(existingUser.subscriptionStatus)
                    .setHasDeviceOwner(existingUser.hasDeviceOwner)
                    .setSelectedPlan(existingUser.selectedPlan)
                    .setCommitmentDays(existingUser.commitmentDays)
                    .setCommitmentStartDate(existingUser.commitmentStartDate)
                    .setCommitmentEndDate(existingUser.commitmentEndDate)
                    .setCreatedAt(existingUser.createdAt)
                    .setUpdatedAt(Timestamp.now())
                    .build()

                val updatedIdentifierUser = IdentifierUserBuilder
                    .fromUser(userId, updatedUser)
                    .build()

                userRepository.setCurrentUser(updatedIdentifierUser)

                Toast.makeText(
                    this@UnauthorizedDeviceActivity,
                    "Account transferred to this device.",
                    Toast.LENGTH_SHORT
                ).show()

                startActivity(Intent(this@UnauthorizedDeviceActivity, MainActivity::class.java))
                finish()

            } catch (e: Exception) {
                Toast.makeText(
                    this@UnauthorizedDeviceActivity,
                    "Transfer failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}