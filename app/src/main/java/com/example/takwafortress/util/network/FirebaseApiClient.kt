package com.example.takwafortress.util.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for Firebase Cloud Functions API.
 * Handles license validation and revocation.
 */
class FirebaseApiClient {

    companion object {
        // TODO: Replace with your actual Firebase Cloud Functions URL
        private const val BASE_URL = "https://us-central1-taqwa-fortress.cloudfunctions.net"
        private const val VALIDATE_LICENSE_ENDPOINT = "$BASE_URL/validateLicense"
        private const val REVOKE_LICENSE_ENDPOINT = "$BASE_URL/revokeLicense"
        private const val ACTIVATE_LICENSE_ENDPOINT = "$BASE_URL/activateLicense"
    }

    /**
     * Validates a license key with Firebase.
     */
    suspend fun validateLicense(
        licenseKey: String,
        hardwareId: String
    ): LicenseValidationResponse = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("licenseKey", licenseKey)
                put("hardwareId", hardwareId)
            }

            val response = makePostRequest(VALIDATE_LICENSE_ENDPOINT, requestBody)

            LicenseValidationResponse(
                isValid = response.getBoolean("isValid"),
                userId = response.optString("userId", null),
                email = response.optString("email", null),
                purchaseDate = response.optLong("purchaseDate", 0),
                isActive = response.optBoolean("isActive", false),
                isRevoked = response.optBoolean("isRevoked", false),
                boundDeviceId = response.optString("boundDeviceId", null),
                errorMessage = response.optString("error", null)
            )
        } catch (e: Exception) {
            LicenseValidationResponse(
                isValid = false,
                errorMessage = "Network error: ${e.message}"
            )
        }
    }

    /**
     * Activates a license (binds it to this device).
     */
    suspend fun activateLicense(
        licenseKey: String,
        hardwareId: String,
        email: String
    ): LicenseActivationResponse = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("licenseKey", licenseKey)
                put("hardwareId", hardwareId)
                put("email", email)
            }

            val response = makePostRequest(ACTIVATE_LICENSE_ENDPOINT, requestBody)

            LicenseActivationResponse(
                success = response.getBoolean("success"),
                userId = response.optString("userId", null),
                message = response.optString("message", null),
                errorMessage = response.optString("error", null)
            )
        } catch (e: Exception) {
            LicenseActivationResponse(
                success = false,
                errorMessage = "Network error: ${e.message}"
            )
        }
    }

    /**
     * Revokes a license (factory reset detected).
     */
    suspend fun revokeLicense(
        licenseKey: String,
        reason: String
    ): LicenseRevocationResponse = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("licenseKey", licenseKey)
                put("reason", reason)
                put("timestamp", System.currentTimeMillis())
            }

            val response = makePostRequest(REVOKE_LICENSE_ENDPOINT, requestBody)

            LicenseRevocationResponse(
                success = response.getBoolean("success"),
                message = response.optString("message", null),
                errorMessage = response.optString("error", null)
            )
        } catch (e: Exception) {
            LicenseRevocationResponse(
                success = false,
                errorMessage = "Network error: ${e.message}"
            )
        }
    }

    /**
     * Makes a POST request to the given URL.
     */
    private fun makePostRequest(urlString: String, body: JSONObject): JSONObject {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000 // 10 seconds
            connection.readTimeout = 10000

            // Write request body
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            // Read response
            val responseCode = connection.responseCode
            val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val response = BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }

            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Checks internet connectivity.
     */
    suspend fun hasInternetConnection(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val connection = URL("https://www.google.com").openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.connect()
            connection.responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            false
        }
    }
}

data class LicenseValidationResponse(
    val isValid: Boolean,
    val userId: String? = null,
    val email: String? = null,
    val purchaseDate: Long = 0,
    val isActive: Boolean = false,
    val isRevoked: Boolean = false,
    val boundDeviceId: String? = null,
    val errorMessage: String? = null
)

data class LicenseActivationResponse(
    val success: Boolean,
    val userId: String? = null,
    val message: String? = null,
    val errorMessage: String? = null
)

data class LicenseRevocationResponse(
    val success: Boolean,
    val message: String? = null,
    val errorMessage: String? = null
)