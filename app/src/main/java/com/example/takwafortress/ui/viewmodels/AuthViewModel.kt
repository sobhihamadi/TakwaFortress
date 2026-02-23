package com.example.takwafortress.ui.viewmodels

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.takwafortress.auth.AuthManager
import com.example.takwafortress.model.builders.IdentifierUserBuilder
import com.example.takwafortress.model.builders.UserBuilder
import com.example.takwafortress.model.enums.SubscriptionStatus
import com.example.takwafortress.model.entities.IdentifierUser
import com.example.takwafortress.repository.implementations.FirebaseUserRepository
import com.example.takwafortress.repository.implementations.LocalDeviceInfoRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = AuthManager(application)
    private val userRepository = FirebaseUserRepository(application)
    private val deviceInfoRepository = LocalDeviceInfoRepository(application)
    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    private val _user = MutableLiveData<IdentifierUser?>()
    val user: LiveData<IdentifierUser?> = _user

    private fun getRealDeviceId(): String {
        return Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }

    fun registerWithEmail(email: String, password: String, selectedPlan: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val authResult = authManager.registerWithEmail(email, password)
            authResult.fold(
                onSuccess = { firebaseUser ->
                    try {
                        // Send verification email BEFORE creating Firestore document.
                        // The user must verify before we complete their registration flow.
                        firebaseUser.sendEmailVerification().await()

                        // Store the plan so we can use it after verification
                        _authState.value = AuthState.AwaitingEmailVerification(
                            email      = firebaseUser.email ?: email,
                            userId     = firebaseUser.uid,
                            selectedPlan = selectedPlan
                        )
                    } catch (e: Exception) {
                        _authState.value = AuthState.Error(
                            "Account created but failed to send verification email: ${e.message}"
                        )
                    }
                },
                onFailure = { exception ->
                    _authState.value = AuthState.Error(exception.message ?: "Registration failed")
                }
            )
        }
    }

    /**
     * Called when the user taps "I've Verified My Email".
     * Reloads the Firebase user to get the latest emailVerified flag,
     * then either completes registration or tells the user to verify first.
     */
    fun checkEmailVerificationAndComplete(userId: String, email: String, selectedPlan: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                // Reload forces Firebase to re-fetch the current emailVerified status
                firebaseAuth.currentUser?.reload()?.await()

                val isVerified = firebaseAuth.currentUser?.isEmailVerified ?: false

                if (isVerified) {
                    // Email confirmed — now safe to create the Firestore document
                    createUserDocument(userId, email, selectedPlan)
                } else {
                    // Not verified yet — bounce back to the waiting screen
                    _authState.value = AuthState.EmailNotYetVerified(
                        email        = email,
                        userId       = userId,
                        selectedPlan = selectedPlan
                    )
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Failed to check verification: ${e.message}")
            }
        }
    }

    /**
     * Resends the verification email.
     * Called when the user taps "Resend Email".
     */
    fun resendVerificationEmail() {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                firebaseAuth.currentUser?.sendEmailVerification()?.await()
                _authState.value = AuthState.VerificationEmailResent
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Failed to resend email: ${e.message}")
            }
        }
    }

    fun loginWithEmail(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = authManager.loginWithEmail(email, password)
            result.fold(
                onSuccess = { firebaseUser ->
                    // Reload to get latest emailVerified status
                    firebaseUser.reload().await()

                    if (!firebaseUser.isEmailVerified) {
                        // Account exists but email not verified — prompt them
                        _authState.value = AuthState.AwaitingEmailVerification(
                            email        = firebaseUser.email ?: email,
                            userId       = firebaseUser.uid,
                            selectedPlan = ""   // already registered, plan is in Firestore
                        )
                    } else {
                        loadUserData(firebaseUser.uid)
                    }
                },
                onFailure = { exception ->
                    _authState.value = AuthState.Error(exception.message ?: "Login failed")
                }
            )
        }
    }

    fun signInWithGoogle(idToken: String, selectedPlan: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = authManager.signInWithGoogle(idToken)
            result.fold(
                onSuccess = { firebaseUser ->
                    val userId = firebaseUser.uid
                    val email  = firebaseUser.email ?: ""
                    try {
                        val existingUser = userRepository.get(userId)
                        userRepository.setCurrentUser(existingUser)
                        _user.value = existingUser
                        _authState.value = AuthState.Success(existingUser)
                    } catch (e: Exception) {
                        // Google accounts are pre-verified — no email check needed
                        createUserDocument(userId, email, selectedPlan)
                    }
                },
                onFailure = { exception ->
                    _authState.value = AuthState.Error(exception.message ?: "Google sign-in failed")
                }
            )
        }
    }

    fun sendPasswordResetEmail(email: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = authManager.sendPasswordResetEmail(email)
            result.fold(
                onSuccess = { _authState.value = AuthState.PasswordResetSent },
                onFailure = { exception ->
                    _authState.value = AuthState.Error(
                        exception.message ?: "Failed to send reset email"
                    )
                }
            )
        }
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    fun validateEmail(email: String): String? {
        if (email.isBlank()) return "Email is required"
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "Invalid email format"
        return null
    }

    fun validatePassword(password: String): String? {
        if (password.length < 6) return "Password must be at least 6 characters"
        return null
    }

    fun validatePasswordConfirmation(password: String, confirmPassword: String): String? {
        if (password != confirmPassword) return "Passwords do not match"
        return null
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun createUserDocument(userId: String, email: String, selectedPlan: String) {
        val realDeviceId = getRealDeviceId()

        val user = UserBuilder.newBuilder()
            .setEmail(email)
            .setDeviceId(realDeviceId)
            .setSubscriptionStatus(SubscriptionStatus.PENDING)
            .setHasDeviceOwner(false)
            .setSelectedPlan(selectedPlan)
            .setCreatedAt(Timestamp.now())
            .setUpdatedAt(Timestamp.now())
            .build()

        val identifierUser = IdentifierUserBuilder.fromUser(userId, user).build()

        try {
            userRepository.setCurrentUser(identifierUser)
            _user.value = identifierUser
            _authState.value = AuthState.Success(identifierUser)
        } catch (e: Exception) {
            _authState.value = AuthState.Error("Failed to create user: ${e.message}")
        }
    }

    private suspend fun loadUserData(userId: String) {
        try {
            val user = userRepository.get(userId)
            userRepository.setCurrentUser(user)
            _user.value = user
            _authState.value = AuthState.Success(user)
        } catch (e: Exception) {
            _authState.value = AuthState.Error("Failed to load user data: ${e.message}")
        }
    }

    fun checkExistingUser() {
        viewModelScope.launch {
            val currentUser = userRepository.getCurrentUser()
            if (currentUser != null) {
                _user.value = currentUser
                _authState.value = AuthState.AlreadyLoggedIn(currentUser)
            } else {
                _authState.value = AuthState.NotLoggedIn
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
            userRepository.clearCurrentUser()
            _user.value = null
            _authState.value = AuthState.NotLoggedIn
        }
    }
}

sealed class AuthState {
    object Loading               : AuthState()
    object NotLoggedIn           : AuthState()
    object PasswordResetSent     : AuthState()
    object VerificationEmailResent : AuthState()

    data class Success(val user: IdentifierUser) : AuthState()
    data class AlreadyLoggedIn(val user: IdentifierUser) : AuthState()
    data class Error(val message: String) : AuthState()

    /** Account created, verification email sent — show "check your inbox" UI */
    data class AwaitingEmailVerification(
        val email: String,
        val userId: String,
        val selectedPlan: String
    ) : AuthState()

    /** User tapped "I've verified" but Firebase still shows emailVerified = false */
    data class EmailNotYetVerified(
        val email: String,
        val userId: String,
        val selectedPlan: String
    ) : AuthState()
}