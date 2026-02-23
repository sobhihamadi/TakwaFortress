package com.example.takwafortress.repository.implementations

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.takwafortress.mappers.toFirestoreMap
import com.example.takwafortress.mappers.toIdentifierUser
import com.example.takwafortress.model.entities.IdentifierUser
import com.example.takwafortress.model.interfaces.ID
import com.example.takwafortress.repository.interfaces.IUserRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Firebase Firestore implementation of IUserRepository.
 *
 * This class follows the Dependency Inversion Principle (DIP) by implementing
 * the IUserRepository interface, allowing for easy testing and swapping
 * of implementations.
 *
 * It uses Firestore as the primary data source and EncryptedSharedPreferences
 * for caching the current user ID locally.
 */
class FirebaseUserRepository(context: Context) : IUserRepository {

    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")

    // Encrypted local storage for current user reference
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "taqwa_user_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val currentUserKey = "current_user_id"

    /**
     * Creates a new user in Firestore.
     *
     * @param item The user to create
     * @return The ID of the created user
     */
    override suspend fun create(item: IdentifierUser): ID = withContext(Dispatchers.IO) {
        val id = item.getId()
        val userData = item.toFirestoreMap()

        usersCollection.document(id)
            .set(userData)
            .await()

        id
    }

    /**
     * Retrieves a user by ID from Firestore.
     *
     * @param id The user ID (Firebase Auth UID)
     * @return The user if found
     * @throws ItemNotFoundException if user doesn't exist
     */
    override suspend fun get(id: ID): IdentifierUser = withContext(Dispatchers.IO) {
        val document = usersCollection.document(id).get().await()

        if (!document.exists()) {
            throw ItemNotFoundException("User with ID $id not found")
        }

        val data = document.data ?: throw ItemNotFoundException("User data is null for ID $id")
        data.toIdentifierUser(id)
    }

    /**
     * Retrieves all users from Firestore.
     *
     * Note: This method should be used carefully as it can be expensive
     * with large datasets.
     *
     * @return List of all users
     */
    override suspend fun getAll(): List<IdentifierUser> = withContext(Dispatchers.IO) {
        val snapshot = usersCollection.get().await()
        snapshot.documents.mapNotNull { document ->
            try {
                val data = document.data ?: return@mapNotNull null
                data.toIdentifierUser(document.id)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Updates an existing user in Firestore.
     *
     * @param item The user with updated values
     * @throws ItemNotFoundException if user doesn't exist
     */
    override suspend fun update(item: IdentifierUser): Unit = withContext(Dispatchers.IO) {
        val id = item.getId()

        if (!exists(id)) {
            throw ItemNotFoundException("User with ID $id not found")
        }

        val userData = item.toFirestoreMap().toMutableMap()
        userData["updatedAt"] = Timestamp.now()

        usersCollection.document(id)
            .set(userData)
            .await()
    }

    /**
     * Deletes a user from Firestore.
     *
     * @param id The user ID to delete
     * @throws ItemNotFoundException if user doesn't exist
     */
    override suspend fun delete(id: ID): Unit = withContext(Dispatchers.IO) {
        if (!exists(id)) {
            throw ItemNotFoundException("User with ID $id not found")
        }

        usersCollection.document(id).delete().await()

        // Clear current user if it's the deleted user
        if (getCurrentUserId() == id) {
            clearCurrentUser()
        }
    }

    /**
     * Checks if a user exists in Firestore.
     *
     * @param id The user ID to check
     * @return true if user exists, false otherwise
     */
    override suspend fun exists(id: ID): Boolean = withContext(Dispatchers.IO) {
        val document = usersCollection.document(id).get().await()
        document.exists()
    }

    /**
     * Retrieves a user by email address.
     *
     * @param email The email to search for
     * @return The user if found
     * @throws ItemNotFoundException if no user found
     */
    override suspend fun getByEmail(email: String): IdentifierUser = withContext(Dispatchers.IO) {
        val query = usersCollection
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .await()

        if (query.isEmpty) {
            throw ItemNotFoundException("User with email $email not found")
        }

        val document = query.documents[0]
        val data = document.data ?: throw ItemNotFoundException("User data is null")
        data.toIdentifierUser(document.id)
    }

    /**
     * Retrieves a user by device ID.
     *
     * @param deviceId The device ID to search for
     * @return The user if found
     * @throws ItemNotFoundException if no user found
     */
    override suspend fun getByDeviceId(deviceId: String): IdentifierUser = withContext(Dispatchers.IO) {
        val query = usersCollection
            .whereEqualTo("deviceId", deviceId)
            .limit(1)
            .get()
            .await()

        if (query.isEmpty) {
            throw ItemNotFoundException("User with device ID $deviceId not found")
        }

        val document = query.documents[0]
        val data = document.data ?: throw ItemNotFoundException("User data is null")
        data.toIdentifierUser(document.id)
    }

    /**
     * Checks if a user exists with given email.
     */
    override suspend fun existsByEmail(email: String): Boolean = withContext(Dispatchers.IO) {
        try {
            getByEmail(email)
            true
        } catch (e: ItemNotFoundException) {
            false
        }
    }

    /**
     * Checks if a user exists with given device ID.
     */
    override suspend fun existsByDeviceId(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            getByDeviceId(deviceId)
            true
        } catch (e: ItemNotFoundException) {
            false
        }
    }

    /**
     * Gets the current active user.
     *
     * @return The current user or null if no user is logged in
     */
    override suspend fun getCurrentUser(): IdentifierUser? = withContext(Dispatchers.IO) {
        val currentUserId = getCurrentUserId() ?: return@withContext null

        try {
            get(currentUserId)
        } catch (e: ItemNotFoundException) {
            // Current user ID exists but user data doesn't - clear it
            clearCurrentUser()
            null
        }
    }

    /**
     * Sets the current active user.
     *
     * This saves the user to Firestore and marks them as current in local storage.
     *
     * @param user The user to set as current
     */
    override suspend fun setCurrentUser(user: IdentifierUser) = withContext(Dispatchers.IO) {
        // Save or update user in Firestore
        if (exists(user.getId())) {
            update(user)
        } else {
            create(user)
        }

        // Save current user ID locally
        sharedPreferences.edit()
            .putString(currentUserKey, user.getId())
            .apply()
    }

    /**
     * Clears the current user reference (logout).
     */
    override suspend fun clearCurrentUser() = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .remove(currentUserKey)
            .apply()
    }

    /**
     * Gets the current user ID from local storage.
     */
    private fun getCurrentUserId(): String? {
        return sharedPreferences.getString(currentUserKey, null)
    }

    // Legacy methods for backward compatibility

    @Deprecated("Use Firebase Auth UID instead")
    override suspend fun getByLicenseKey(licenseKey: String): IdentifierUser = withContext(Dispatchers.IO) {
        val query = usersCollection
            .whereEqualTo("licenseKey", licenseKey)
            .limit(1)
            .get()
            .await()

        if (query.isEmpty) {
            throw ItemNotFoundException("User with license key $licenseKey not found")
        }

        val document = query.documents[0]
        val data = document.data ?: throw ItemNotFoundException("User data is null")
        data.toIdentifierUser(document.id)
    }

    @Deprecated("Use Firebase Auth UID instead")
    override suspend fun existsByLicenseKey(licenseKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            getByLicenseKey(licenseKey)
            true
        } catch (e: ItemNotFoundException) {
            false
        }
    }
}

/**
 * Exception thrown when an item is not found in the repository.
 */
class ItemNotFoundException(message: String) : Exception(message)