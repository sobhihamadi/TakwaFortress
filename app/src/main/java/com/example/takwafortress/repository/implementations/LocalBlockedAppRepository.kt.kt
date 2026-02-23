package com.example.takwafortress.repository.implementations

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.takwafortress.mappers.BlockedAppMapper
import com.example.takwafortress.model.entities.IdentifierBlockedApp
import com.example.takwafortress.model.interfaces.ID
import com.example.takwafortress.repository.interfaces.IBlockedAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LocalBlockedAppRepository(context: Context) : IBlockedAppRepository {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "taqwa_blocked_apps_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val mapper = BlockedAppMapper()
    private val allBlockedAppsKey = "all_blocked_apps_ids"

    override suspend fun create(item: IdentifierBlockedApp): ID = withContext(Dispatchers.IO) {
        val id = item.getId()
        val json = mapper.reverseMap(item)

        sharedPreferences.edit().apply {
            putString("blocked_app_$id", json.toString())

            val existingIds = getAllBlockedAppIds().toMutableSet()
            existingIds.add(id)
            putString(allBlockedAppsKey, existingIds.joinToString(","))

            apply()
        }

        id
    }

    override suspend fun get(id: ID): IdentifierBlockedApp = withContext(Dispatchers.IO) {
        val jsonString = sharedPreferences.getString("blocked_app_$id", null)
            ?: throw ItemNotFoundException("BlockedApp with ID $id not found")

        val json = JSONObject(jsonString)
        mapper.map(json)
    }

    override suspend fun getAll(): List<IdentifierBlockedApp> = withContext(Dispatchers.IO) {
        val ids = getAllBlockedAppIds()
        ids.mapNotNull { id ->
            try {
                get(id)
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun update(item: IdentifierBlockedApp) {
        withContext(Dispatchers.IO) {
            val id = item.getId()
            if (!exists(id)) {
                throw ItemNotFoundException("BlockedApp with ID $id not found")
            }

            val json = mapper.reverseMap(item)
            sharedPreferences.edit()
                .putString("blocked_app_$id", json.toString())
                .apply()
        }
    }

    override suspend fun delete(id: ID) {
        withContext(Dispatchers.IO) {
            if (!exists(id)) {
                throw ItemNotFoundException("BlockedApp with ID $id not found")
            }

            sharedPreferences.edit().apply {
                remove("blocked_app_$id")

                val existingIds = getAllBlockedAppIds().toMutableSet()
                existingIds.remove(id)
                putString(allBlockedAppsKey, existingIds.joinToString(","))

                apply()
            }
        }
    }

    override suspend fun exists(id: ID): Boolean = withContext(Dispatchers.IO) {
        sharedPreferences.contains("blocked_app_$id")
    }

    override suspend fun getByPackageName(packageName: String): IdentifierBlockedApp = withContext(Dispatchers.IO) {
        getAll().find { it.getPackageName() == packageName }
            ?: throw ItemNotFoundException("BlockedApp with package $packageName not found")
    }

    override suspend fun isAppBlocked(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            getByPackageName(packageName)
            true
        } catch (e: ItemNotFoundException) {
            false
        }
    }

    override suspend fun getAllBlockedApps(): List<IdentifierBlockedApp> = withContext(Dispatchers.IO) {
        getAll()
    }

    override suspend fun getSuspendedApps(): List<IdentifierBlockedApp> = withContext(Dispatchers.IO) {
        getAll().filter { it.getIsSuspended() && !it.isBlacklistedApp() }
    }

    override suspend fun getNuclearApps(): List<IdentifierBlockedApp> = withContext(Dispatchers.IO) {
        getAll().filter { it.isBlacklistedApp() }
    }

    override suspend fun blockApp(app: IdentifierBlockedApp) {
        withContext(Dispatchers.IO) {
            create(app)
        }
    }

    override suspend fun unblockApp(packageName: String) {
        withContext(Dispatchers.IO) {
            val app = getByPackageName(packageName)
            delete(app.getId())
        }
    }

    override suspend fun updateSuspensionStatus(packageName: String, isSuspended: Boolean) {
        withContext(Dispatchers.IO) {
            val app = getByPackageName(packageName)

            val updatedApp = com.example.takwafortress.model.builders.IdentifierBlockedAppBuilder.newBuilder()
                .setId(app.getId())
                .setBlockedApp(
                    com.example.takwafortress.model.builders.BlockedAppBuilder.newBuilder()
                        .setPackageName(app.getPackageName())
                        .setAppName(app.getAppName())
                        .setIsSystemApp(app.getIsSystemApp())
                        .setIsSuspended(isSuspended)
                        .setBlockReason(app.getBlockReason())
                        .setDetectedDate(app.getDetectedDate())
                        .build()
                )
                .build()

            update(updatedApp)
        }
    }

    private fun getAllBlockedAppIds(): List<String> {
        val idsString = sharedPreferences.getString(allBlockedAppsKey, "") ?: ""
        return if (idsString.isEmpty()) {
            emptyList()
        } else {
            idsString.split(",")
        }
    }
}