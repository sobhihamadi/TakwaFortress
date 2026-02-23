package com.example.takwafortress.repository.implementations

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.takwafortress.mappers.LicenseMapper
import com.example.takwafortress.model.entities.IdentifierLicense
import com.example.takwafortress.model.interfaces.ID
import com.example.takwafortress.repository.interfaces.ILicenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LocalLicenseRepository(context: Context) : ILicenseRepository {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "taqwa_license_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val mapper = LicenseMapper()
    private val allLicensesKey = "all_licenses_ids"
    private val currentLicenseKey = "current_license_id"

    override suspend fun create(item: IdentifierLicense): ID = withContext(Dispatchers.IO) {
        val id = item.getId()
        val json = mapper.reverseMap(item)

        sharedPreferences.edit().apply {
            putString("license_$id", json.toString())

            val existingIds = getAllLicenseIds().toMutableSet()
            existingIds.add(id)
            putString(allLicensesKey, existingIds.joinToString(","))

            apply()
        }

        id
    }

    override suspend fun get(id: ID): IdentifierLicense = withContext(Dispatchers.IO) {
        val jsonString = sharedPreferences.getString("license_$id", null)
            ?: throw ItemNotFoundException("License with ID $id not found")

        val json = JSONObject(jsonString)
        mapper.map(json)
    }

    override suspend fun getAll(): List<IdentifierLicense> = withContext(Dispatchers.IO) {
        val ids = getAllLicenseIds()
        ids.mapNotNull { id ->
            try {
                get(id)
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun update(item: IdentifierLicense) = withContext(Dispatchers.IO) {
        val id = item.getId()
        if (!exists(id)) {
            throw ItemNotFoundException("License with ID $id not found")
        }

        val json = mapper.reverseMap(item)
        sharedPreferences.edit()
            .putString("license_$id", json.toString())
            .apply()
    }

    override suspend fun delete(id: ID): Unit = withContext(Dispatchers.IO) {
        if (!exists(id)) {
            throw ItemNotFoundException("License with ID $id not found")
        }

        sharedPreferences.edit().apply {
            remove("license_$id")

            val existingIds = getAllLicenseIds().toMutableSet()
            existingIds.remove(id)
            putString(allLicensesKey, existingIds.joinToString(","))

            apply()
        }
        Unit
    }

    override suspend fun exists(id: ID): Boolean = withContext(Dispatchers.IO) {
        sharedPreferences.contains("license_$id")
    }

    override suspend fun getByLicenseKey(licenseKey: String): IdentifierLicense = withContext(Dispatchers.IO) {
        getAll().find { it.getLicenseKey() == licenseKey }
            ?: throw ItemNotFoundException("License with key $licenseKey not found")
    }

    override suspend fun getByDeviceId(deviceId: String): IdentifierLicense = withContext(Dispatchers.IO) {
        getAll().find { it.getDeviceId() == deviceId }
            ?: throw ItemNotFoundException("License for device $deviceId not found")
    }

    override suspend fun isLicenseValid(licenseKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val license = getByLicenseKey(licenseKey)
            license.isValid()
        } catch (e: ItemNotFoundException) {
            false
        }
    }

    override suspend fun revokeLicense(licenseKey: String, reason: String) = withContext(Dispatchers.IO) {
        val license = getByLicenseKey(licenseKey)

        // Create updated license with revoked status
        val updatedLicense = com.example.takwafortress.model.builders.IdentifierLicenseBuilder.newBuilder()
            .setId(license.getId())
            .setLicense(
                com.example.takwafortress.model.builders.LicenseBuilder.newBuilder()
                    .setLicenseKey(license.getLicenseKey())
                    .setUserId(license.getUserId())
                    .setDeviceId(license.getDeviceId())
                    .setIsActive(false)
                    .setActivationDate(license.getActivationDate())
                    .setIsRevoked(true)
                    .setRevokeReason(reason)
                    .build()
            )
            .build()

        update(updatedLicense)
    }

    override suspend fun getCurrentLicense(): IdentifierLicense? = withContext(Dispatchers.IO) {
        val currentLicenseId = sharedPreferences.getString(currentLicenseKey, null)
        currentLicenseId?.let {
            try {
                get(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun setCurrentLicense(license: IdentifierLicense) = withContext(Dispatchers.IO) {
        create(license)

        sharedPreferences.edit()
            .putString(currentLicenseKey, license.getId())
            .apply()
    }

    private fun getAllLicenseIds(): List<String> {
        val idsString = sharedPreferences.getString(allLicensesKey, "") ?: ""
        return if (idsString.isEmpty()) {
            emptyList()
        } else {
            idsString.split(",")
        }
    }
}