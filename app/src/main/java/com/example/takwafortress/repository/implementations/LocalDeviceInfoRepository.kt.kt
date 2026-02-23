package com.example.takwafortress.repository.implementations

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.takwafortress.mappers.DeviceInfoMapper
import com.example.takwafortress.model.entities.IdentifierDeviceInfo
import com.example.takwafortress.model.interfaces.ID
import com.example.takwafortress.repository.interfaces.IDeviceInfoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LocalDeviceInfoRepository(context: Context) : IDeviceInfoRepository {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "taqwa_device_info_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val mapper = DeviceInfoMapper()
    private val allDeviceInfosKey = "all_device_infos_ids"
    private val currentDeviceInfoKey = "current_device_info_id"

    override suspend fun create(item: IdentifierDeviceInfo): ID = withContext(Dispatchers.IO) {
        val id = item.getId()
        val json = mapper.reverseMap(item)

        sharedPreferences.edit().apply {
            putString("device_info_$id", json.toString())

            val existingIds = getAllDeviceInfoIds().toMutableSet()
            existingIds.add(id)
            putString(allDeviceInfosKey, existingIds.joinToString(","))

            apply()
        }

        id
    }

    override suspend fun get(id: ID): IdentifierDeviceInfo = withContext(Dispatchers.IO) {
        val jsonString = sharedPreferences.getString("device_info_$id", null)
            ?: throw ItemNotFoundException("DeviceInfo with ID $id not found")

        val json = JSONObject(jsonString)
        mapper.map(json)
    }

    override suspend fun getAll(): List<IdentifierDeviceInfo> = withContext(Dispatchers.IO) {
        val ids = getAllDeviceInfoIds()
        ids.mapNotNull { id ->
            try {
                get(id)
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun update(item: IdentifierDeviceInfo) {
        withContext(Dispatchers.IO) {
            val id = item.getId()
            if (!exists(id)) {
                throw ItemNotFoundException("DeviceInfo with ID $id not found")
            }

            val json = mapper.reverseMap(item)
            sharedPreferences.edit()
                .putString("device_info_$id", json.toString())
                .apply()
        }
    }

    override suspend fun delete(id: ID) {
        withContext(Dispatchers.IO) {
            if (!exists(id)) {
                throw ItemNotFoundException("DeviceInfo with ID $id not found")
            }

            sharedPreferences.edit().apply {
                remove("device_info_$id")

                val existingIds = getAllDeviceInfoIds().toMutableSet()
                existingIds.remove(id)
                putString(allDeviceInfosKey, existingIds.joinToString(","))

                apply()
            }
        }
    }

    override suspend fun exists(id: ID): Boolean = withContext(Dispatchers.IO) {
        sharedPreferences.contains("device_info_$id")
    }

    override suspend fun getCurrentDeviceInfo(): IdentifierDeviceInfo? = withContext(Dispatchers.IO) {
        val currentDeviceInfoId = sharedPreferences.getString(currentDeviceInfoKey, null)
        currentDeviceInfoId?.let {
            try {
                get(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun setCurrentDeviceInfo(deviceInfo: IdentifierDeviceInfo) {
        withContext(Dispatchers.IO) {
            create(deviceInfo)

            sharedPreferences.edit()
                .putString(currentDeviceInfoKey, deviceInfo.getId())
                .apply()
        }
    }

    override suspend fun getByHardwareId(hardwareId: String): IdentifierDeviceInfo = withContext(Dispatchers.IO) {
        getAll().find { it.getHardwareId() == hardwareId }
            ?: throw ItemNotFoundException("DeviceInfo with hardware ID $hardwareId not found")
    }

    override suspend fun existsByHardwareId(hardwareId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            getByHardwareId(hardwareId)
            true
        } catch (e: ItemNotFoundException) {
            false
        }
    }

    override suspend fun updateFirstInstallDate(hardwareId: String, firstInstallDate: Long) {
        withContext(Dispatchers.IO) {
            val deviceInfo = getByHardwareId(hardwareId)

            val updatedDeviceInfo = com.example.takwafortress.model.builders.IdentifierDeviceInfoBuilder.newBuilder()
                .setId(deviceInfo.getId())
                .setDeviceInfo(
                    com.example.takwafortress.model.builders.DeviceInfoBuilder.newBuilder()
                        .setHardwareId(deviceInfo.getHardwareId())
                        .setBrand(deviceInfo.getBrand())
                        .setModel(deviceInfo.getModel())
                        .setAndroidVersion(deviceInfo.getAndroidVersion())
                        .setHasKnoxSupport(deviceInfo.getHasKnoxSupport())
                        .setActivationMethod(deviceInfo.getActivationMethod())
                        .setFirstInstallDate(firstInstallDate)
                        .build()
                )
                .build()

            update(updatedDeviceInfo)
        }
    }

    private fun getAllDeviceInfoIds(): List<String> {
        val idsString = sharedPreferences.getString(allDeviceInfosKey, "") ?: ""
        return if (idsString.isEmpty()) {
            emptyList()
        } else {
            idsString.split(",")
        }
    }
}