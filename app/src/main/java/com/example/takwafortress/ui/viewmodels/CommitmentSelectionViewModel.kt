package com.example.takwafortress.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.takwafortress.model.builders.IdentifierUserBuilder
import com.example.takwafortress.model.builders.UserBuilder
import com.example.takwafortress.model.enums.CommitmentPlan
import com.example.takwafortress.model.enums.SubscriptionStatus
import com.example.takwafortress.repository.implementations.FirebaseUserRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class CommitmentSelectionViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepository = FirebaseUserRepository(application)
    private val auth = FirebaseAuth.getInstance()

    private val _selectionState = MutableLiveData<CommitmentSelectionState>(CommitmentSelectionState.Ready)
    val selectionState: LiveData<CommitmentSelectionState> = _selectionState

    private val _selectedPlan = MutableLiveData<CommitmentPlan>()
    val selectedPlan: LiveData<CommitmentPlan> = _selectedPlan

    fun selectPlan(plan: CommitmentPlan) {
        _selectedPlan.value = plan
    }

    /**
     * Called when user taps the proceed button.
     * Free trial → save TRIAL status immediately and proceed.
     * Paid plan → open Whop checkout WebView.
     */
    fun proceedWithPlan() {
        val plan = _selectedPlan.value ?: run {
            _selectionState.value = CommitmentSelectionState.Error("Please select a plan first")
            return
        }

        if (plan.isFree) {
            // Free trial — save to Firestore immediately, no payment needed
            saveSubscriptionAndProceed(plan, SubscriptionStatus.TRIAL)
        } else {
            // Paid plan — open Whop WebView checkout
            _selectionState.value = CommitmentSelectionState.OpenWhopCheckout(plan)
        }
    }

    /**
     * Called by the Activity after WebView detects the Whop success redirect URL.
     */
    fun onPaymentConfirmed(plan: CommitmentPlan) {
        saveSubscriptionAndProceed(plan, SubscriptionStatus.ACTIVE)
    }

    /**
     * Called by the Activity when user closes the WebView without paying.
     */
    fun onPaymentCancelled() {
        _selectionState.value = CommitmentSelectionState.Ready
    }

    /**
     * Saves the selected plan + subscription status to Firestore,
     * then emits PaymentSuccess so the Activity can navigate forward.
     */
    private fun saveSubscriptionAndProceed(plan: CommitmentPlan, status: SubscriptionStatus) {
        viewModelScope.launch {
            _selectionState.postValue(CommitmentSelectionState.Saving)

            try {
                val firebaseUserId = auth.currentUser?.uid
                    ?: throw Exception("No authenticated user found")

                // Load existing user from Firestore
                val existingUser = userRepository.get(firebaseUserId)

                // Build updated user with new subscription info
                val updatedUser = UserBuilder.newBuilder()
                    .setEmail(existingUser.email)
                    .setDeviceId(existingUser.deviceId)
                    .setSubscriptionStatus(status)
                    .setSelectedPlan(plan.name)
                    .setHasDeviceOwner(existingUser.hasDeviceOwner)
                    .setCommitmentDays(plan.days)
                    .setCommitmentStartDate(Timestamp.now())
                    .setCommitmentEndDate(
                        Timestamp(
                            Timestamp.now().seconds + plan.getDurationMillis() / 1000,
                            0
                        )
                    )
                    .setCreatedAt(existingUser.createdAt)
                    .setUpdatedAt(Timestamp.now())
                    .build()

                val updatedIdentifierUser = IdentifierUserBuilder
                    .fromUser(firebaseUserId, updatedUser)
                    .build()

                // Save to Firestore
                userRepository.setCurrentUser(updatedIdentifierUser)

                Log.d("CommitmentViewModel", "Saved subscription: $status for plan: ${plan.name}")

                _selectionState.postValue(CommitmentSelectionState.PaymentSuccess(plan))

            } catch (e: Exception) {
                Log.e("CommitmentViewModel", "Failed to save subscription: ${e.message}")
                _selectionState.postValue(
                    CommitmentSelectionState.Error("Failed to save your plan. Please try again.\n${e.message}")
                )
            }
        }
    }

    fun getPlanDetails(plan: CommitmentPlan): String {
        return buildString {
            appendLine(plan.displayName)
            appendLine()
            appendLine("Duration: ${plan.days} days")
            appendLine("Price: ${plan.getPriceDisplay()}")
            appendLine()
            appendLine(plan.description)
            appendLine()
            appendLine("What you get:")
            appendLine("✅ Device locked for ${plan.days} days")
            appendLine("✅ Cannot uninstall Taqwa")
            appendLine("✅ Browsers blocked")
            appendLine("✅ DNS filtering active")
            appendLine("✅ Full protection enabled")
        }.trim()
    }
}

sealed class CommitmentSelectionState {
    object Ready : CommitmentSelectionState()
    object Saving : CommitmentSelectionState()   // Writing to Firestore
    data class OpenWhopCheckout(val plan: CommitmentPlan) : CommitmentSelectionState()
    data class PaymentSuccess(val plan: CommitmentPlan) : CommitmentSelectionState()
    data class Error(val message: String) : CommitmentSelectionState()
}