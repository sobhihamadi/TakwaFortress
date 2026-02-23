package com.example.takwafortress.auth

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Manager class for Firebase Authentication operations.
 *
 * This class follows the Single Responsibility Principle (SRP) by handling
 * only authentication-related operations.
 *
 * It provides a clean abstraction over Firebase Auth, making it easier to
 * test and potentially swap authentication providers.
 */
class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * Gets the currently authenticated user.
     */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /**
     * Gets the current user's UID (used as database ID).
     */
    val currentUserId: String?
        get() = auth.currentUser?.uid

    /**
     * Checks if a user is currently logged in.
     */
    val isUserLoggedIn: Boolean
        get() = currentUser != null

    /**
     * Registers a new user with email and password.
     *
     * @param email User's email address
     * @param password User's password (min 6 characters)
     * @return Result containing FirebaseUser on success or Exception on failure
     */
    suspend fun registerWithEmail(
        email: String,
        password: String
    ): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let {
                Result.success(it)
            } ?: Result.failure(AuthException("Registration failed: User is null"))
        } catch (e: Exception) {
            Result.failure(AuthException("Registration failed: ${e.message}", e))
        }
    }

    /**
     * Logs in a user with email and password.
     *
     * @param email User's email address
     * @param password User's password
     * @return Result containing FirebaseUser on success or Exception on failure
     */
    suspend fun loginWithEmail(
        email: String,
        password: String
    ): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user?.let {
                Result.success(it)
            } ?: Result.failure(AuthException("Login failed: User is null"))
        } catch (e: Exception) {
            Result.failure(AuthException("Login failed: ${e.message}", e))
        }
    }

    /**
     * Signs in with Google using ID token.
     *
     * @param idToken Google ID token obtained from Google Sign-In
     * @return Result containing FirebaseUser on success or Exception on failure
     */
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            result.user?.let {
                Result.success(it)
            } ?: Result.failure(AuthException("Google sign-in failed: User is null"))
        } catch (e: Exception) {
            Result.failure(AuthException("Google sign-in failed: ${e.message}", e))
        }
    }

    /**
     * Sends password reset email.
     *
     * @param email Email address to send reset link to
     * @return Result indicating success or failure
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AuthException("Failed to send reset email: ${e.message}", e))
        }
    }

    /**
     * Signs out the current user.
     */
    fun signOut() {
        auth.signOut()
    }

    /**
     * Re-authenticates the current user with email and password.
     * Required before sensitive operations like account deletion.
     *
     * @param password Current user's password
     * @return Result indicating success or failure
     */
    suspend fun reauthenticate(password: String): Result<Unit> {
        return try {
            val user = currentUser ?: return Result.failure(
                AuthException("No user logged in")
            )
            val email = user.email ?: return Result.failure(
                AuthException("User email is null")
            )

            val credential = com.google.firebase.auth.EmailAuthProvider
                .getCredential(email, password)

            user.reauthenticate(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AuthException("Reauthentication failed: ${e.message}", e))
        }
    }

    /**
     * Deletes the current user account.
     * User must be recently authenticated.
     *
     * @return Result indicating success or failure
     */
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            currentUser?.delete()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AuthException("Failed to delete account: ${e.message}", e))
        }
    }

    /**
     * Updates user's email address.
     * User must be recently authenticated.
     *
     * @param newEmail New email address
     * @return Result indicating success or failure
     */
    suspend fun updateEmail(newEmail: String): Result<Unit> {
        return try {
            currentUser?.verifyBeforeUpdateEmail(newEmail)?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AuthException("Failed to update email: ${e.message}", e))
        }
    }

    /**
     * Updates user's password.
     * User must be recently authenticated.
     *
     * @param newPassword New password
     * @return Result indicating success or failure
     */
    suspend fun updatePassword(newPassword: String): Result<Unit> {
        return try {
            currentUser?.updatePassword(newPassword)?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AuthException("Failed to update password: ${e.message}", e))
        }
    }

    /**
     * Sends email verification to current user.
     *
     * @return Result indicating success or failure
     */
    suspend fun sendEmailVerification(): Result<Unit> {
        return try {
            currentUser?.sendEmailVerification()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AuthException("Failed to send verification: ${e.message}", e))
        }
    }

    /**
     * Checks if current user's email is verified.
     */
    val isEmailVerified: Boolean
        get() = currentUser?.isEmailVerified ?: false
}

/**
 * Custom exception for authentication errors.
 */
class AuthException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)