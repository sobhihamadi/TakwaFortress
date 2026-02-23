package com.example.takwafortress.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.example.takwafortress.R
import kotlinx.coroutines.tasks.await

/**
 * Helper class for Google Sign-In operations.
 *
 * This class follows the Single Responsibility Principle (SRP) by handling
 * only Google Sign-In related operations.
 */
class GoogleSignInHelper(private val context: Context) {

    private val googleSignInClient: GoogleSignInClient

    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }

    /**
     * Gets the intent to start Google Sign-In flow.
     *
     * @return Intent for Google Sign-In activity
     */
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    /**
     * Handles the result from Google Sign-In activity.
     *
     * @param data Intent data from activity result
     * @return Result containing ID token on success or Exception on failure
     */
    fun handleSignInResult(data: Intent?): Result<String> {
        return try {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            account.idToken?.let {
                Result.success(it)
            } ?: Result.failure(GoogleSignInException("ID Token is null"))
        } catch (e: ApiException) {
            Result.failure(GoogleSignInException("Sign in failed: ${e.statusCode}", e))
        } catch (e: Exception) {
            Result.failure(GoogleSignInException("Unexpected error: ${e.message}", e))
        }
    }

    /**
     * Signs out from Google account.
     * This is useful when user wants to switch Google accounts.
     */
    suspend fun signOut(): Result<Unit> {
        return try {
            googleSignInClient.signOut().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(GoogleSignInException("Failed to sign out: ${e.message}", e))
        }
    }

    /**
     * Revokes access to Google account.
     * This removes app's access completely.
     */
    suspend fun revokeAccess(): Result<Unit> {
        return try {
            googleSignInClient.revokeAccess().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(GoogleSignInException("Failed to revoke access: ${e.message}", e))
        }
    }

    /**
     * Gets the currently signed in Google account.
     *
     * @return GoogleSignInAccount if available, null otherwise
     */
    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }
}

/**
 * Custom exception for Google Sign-In errors.
 */
class GoogleSignInException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Extension function to await Google Sign-In task.
 */
