package com.example.takwafortress.repository.implementations

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.takwafortress.mappers.FortressPolicyMapper
import com.example.takwafortress.model.entities.IdentifierFortressPolicy
import com.example.takwafortress.model.enums.FortressState
import com.example.takwafortress.model.interfaces.ID
import com.example.takwafortress.repository.interfaces.IFortressPolicyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LocalFortressPolicyRepository(context: Context) : IFortressPolicyRepository {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "taqwa_fortress_policy_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val mapper = FortressPolicyMapper()
    private val allPoliciesKey = "all_policies_ids"
    private val activePolicyKey = "active_policy_id"

    override suspend fun create(item: IdentifierFortressPolicy): ID = withContext(Dispatchers.IO) {
        val id = item.getId()
        val json = mapper.reverseMap(item)

        sharedPreferences.edit().apply {
            putString("policy_$id", json.toString())

            val existingIds = getAllPolicyIds().toMutableSet()
            existingIds.add(id)
            putString(allPoliciesKey, existingIds.joinToString(","))

            apply()
        }

        id
    }

    override suspend fun get(id: ID): IdentifierFortressPolicy = withContext(Dispatchers.IO) {
        val jsonString = sharedPreferences.getString("policy_$id", null)
            ?: throw ItemNotFoundException("FortressPolicy with ID $id not found")

        val json = JSONObject(jsonString)
        mapper.map(json)
    }

    override suspend fun getAll(): List<IdentifierFortressPolicy> = withContext(Dispatchers.IO) {
        val ids = getAllPolicyIds()
        ids.mapNotNull { id ->
            try {
                get(id)
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun update(item: IdentifierFortressPolicy) {
        withContext(Dispatchers.IO) {
            val id = item.getId()
            if (!exists(id)) {
                throw ItemNotFoundException("FortressPolicy with ID $id not found")
            }

            val json = mapper.reverseMap(item)
            sharedPreferences.edit()
                .putString("policy_$id", json.toString())
                .apply()
        }
    }

    override suspend fun delete(id: ID) {
        withContext(Dispatchers.IO) {
            if (!exists(id)) {
                throw ItemNotFoundException("FortressPolicy with ID $id not found")
            }

            sharedPreferences.edit().apply {
                remove("policy_$id")

                val existingIds = getAllPolicyIds().toMutableSet()
                existingIds.remove(id)
                putString(allPoliciesKey, existingIds.joinToString(","))

                apply()
            }
        }
    }

    override suspend fun exists(id: ID): Boolean = withContext(Dispatchers.IO) {
        sharedPreferences.contains("policy_$id")
    }

    override suspend fun getActivePolicy(): IdentifierFortressPolicy? = withContext(Dispatchers.IO) {
        val activePolicyId = sharedPreferences.getString(activePolicyKey, null)
        activePolicyId?.let {
            try {
                get(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun setActivePolicy(policy: IdentifierFortressPolicy) {
        withContext(Dispatchers.IO) {
            create(policy)

            sharedPreferences.edit()
                .putString(activePolicyKey, policy.getId())
                .apply()
        }
    }

    override suspend fun hasActivePolicy(): Boolean = withContext(Dispatchers.IO) {
        getActivePolicy() != null
    }

    override suspend fun updateFortressState(state: FortressState) {
        withContext(Dispatchers.IO) {
            val activePolicy = getActivePolicy()
            if (activePolicy == null) {
                // No active policy yet - this is okay during activation
                Log.w("FortressRepo", "No active policy to update state - will be set when policy is saved")
                return@withContext
            }

            // Rebuild policy with new state
            val updatedPolicy = com.example.takwafortress.model.builders.IdentifierFortressPolicyBuilder.newBuilder()
                .setId(activePolicy.getId())
                .setFortressPolicy(
                    com.example.takwafortress.model.builders.FortressPolicyBuilder.newBuilder()
                        .setCommitmentPlan(activePolicy.getCommitmentPlan())
                        .setActivationTimestamp(activePolicy.getActivationTimestamp())
                        .setExpiryTimestamp(activePolicy.getExpiryTimestamp())
                        .setIsDeviceOwnerActive(activePolicy.getIsDeviceOwnerActive())
                        .setActivationMethod(activePolicy.getActivationMethod())
                        .setBlockedApps(activePolicy.getBlockedApps())
                        .setIsDnsForced(activePolicy.getIsDnsForced())
                        .setIsSafeModeDisabled(activePolicy.getIsSafeModeDisabled())
                        .setIsFactoryResetBlocked(activePolicy.getIsFactoryResetBlocked())
                        .setIsTimeProtectionActive(activePolicy.getIsTimeProtectionActive())
                        .setCurrentState(state)  // ✅ Update state here
                        .build()
                )
                .build()

            update(updatedPolicy)
        }
    }

    override suspend fun clearActivePolicy() {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .remove(activePolicyKey)
                .apply()
        }
    }

    override suspend fun getHistoricalPolicies(): List<IdentifierFortressPolicy> = withContext(Dispatchers.IO) {
        getAll().filter { it.getFortressState() == FortressState.UNLOCKABLE || it.isExpired() }
    }

    private fun getAllPolicyIds(): List<String> {
        val idsString = sharedPreferences.getString(allPoliciesKey, "") ?: ""
        return if (idsString.isEmpty()) {
            emptyList()
        } else {
            idsString.split(",")
        }
    }
}