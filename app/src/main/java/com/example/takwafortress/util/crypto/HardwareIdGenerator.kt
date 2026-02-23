package com.example.takwafortress.util.crypto


import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.example.takwafortress.util.exceptions.HardwareIdException
import java.security.MessageDigest

object HardwareIdGenerator {

    /**
     * Extracts a unique hardware ID for the device.
     * This combines multiple device identifiers to create a stable, unique ID.
     *
     * Priority:
     * 1. Android ID (most stable after Android 8.0)
     * 2. Build.SERIAL (for older devices)
     * 3. Fallback to generated ID
     *
     * @param context - Application context
     * @return A stable hardware ID for this device
     */
    @SuppressLint("HardwareIds")
    fun extractHardwareId(context: Context): String {
        return try {
            val components = mutableListOf<String>()

            // 1. Android ID (most reliable on Android 8.0+)
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") {
                // "9774d56d682e549c" is a known bug ID on some emulators
                components.add("android_id:$androidId")
            }

            // 2. Build information (stable across factory resets)
            components.add("manufacturer:${Build.MANUFACTURER}")
            components.add("model:${Build.MODEL}")
            components.add("brand:${Build.BRAND}")
            components.add("device:${Build.DEVICE}")
            components.add("board:${Build.BOARD}")

            // 3. Build.SERIAL (deprecated but useful for older devices)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // For Android 8.0+, need READ_PHONE_STATE permission
                    // We'll skip this and rely on Android ID
                } else {
                    @Suppress("DEPRECATION")
                    val serial = Build.SERIAL
                    if (serial != null && serial != "unknown") {
                        components.add("serial:$serial")
                    }
                }
            } catch (e: Exception) {
                // Ignore if permission not granted
            }

            // Combine all components and hash
            val combined = components.joinToString("|")
            val hash = hashString(combined)

            // Return first 32 characters of hash
            hash.substring(0, 32)

        } catch (e: Exception) {
            throw HardwareIdException("Failed to extract hardware ID: ${e.message}")
        }
    }

    /**
     * Gets the device fingerprint (more detailed than hardware ID).
     * Used for fraud detection and device verification.
     */
    @SuppressLint("HardwareIds")
    fun getDeviceFingerprint(context: Context): Map<String, String> {
        return mapOf(
            "android_id" to (Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"),
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "brand" to Build.BRAND,
            "device" to Build.DEVICE,
            "board" to Build.BOARD,
            "hardware" to Build.HARDWARE,
            "product" to Build.PRODUCT,
            "android_version" to Build.VERSION.RELEASE,
            "sdk_int" to Build.VERSION.SDK_INT.toString(),
            "build_id" to Build.ID,
            "fingerprint" to Build.FINGERPRINT
        )
    }

    /**
     * Checks if the current hardware ID matches the stored one.
     * Used to detect factory resets and device changes.
     */
    fun verifyHardwareId(context: Context, storedHardwareId: String): Boolean {
        return try {
            val currentHardwareId = extractHardwareId(context)
            currentHardwareId == storedHardwareId
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Hashes a string using SHA-256.
     */
    private fun hashString(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            throw HardwareIdException("Failed to hash string: ${e.message}")
        }
    }

    /**
     * Gets human-readable device name.
     * Example: "Samsung Galaxy S21" or "Xiaomi Redmi Note 10"
     */
    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL

        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    /**
     * Checks if the device is rooted.
     */
    fun isDeviceRooted(): Boolean {
        // Check for common root files
        val rootPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )

        return rootPaths.any { java.io.File(it).exists() }
    }

    /**
     * Checks if the device is an emulator.
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT)
    }
}