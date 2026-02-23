package com.example.takwafortress.mappers

import com.example.takwafortress.model.builders.FortressPolicyBuilder
import com.example.takwafortress.model.builders.IdentifierFortressPolicyBuilder
import com.example.takwafortress.model.entities.IdentifierFortressPolicy
import com.example.takwafortress.model.enums.ActivationMethod
import com.example.takwafortress.model.enums.CommitmentPlan
import com.example.takwafortress.model.enums.FortressState



import org.json.JSONArray
import org.json.JSONObject

class FortressPolicyMapper : IMapper<JSONObject, IdentifierFortressPolicy> {

    override fun map(data: JSONObject): IdentifierFortressPolicy {
        // Parse blocked apps array
        val blockedAppsArray = data.getJSONArray("blockedApps")
        val blockedApps = mutableSetOf<String>()
        for (i in 0 until blockedAppsArray.length()) {
            blockedApps.add(blockedAppsArray.getString(i))
        }

        val fortressPolicy = FortressPolicyBuilder.newBuilder()
            .setCommitmentPlan(CommitmentPlan.valueOf(data.getString("commitmentPlan")))
            .setActivationTimestamp(data.getLong("activationTimestamp"))
            .setExpiryTimestamp(data.getLong("expiryTimestamp"))
            .setIsDeviceOwnerActive(data.getBoolean("isDeviceOwnerActive"))
            .setActivationMethod(ActivationMethod.valueOf(data.getString("activationMethod")))
            .setBlockedApps(blockedApps)
            .setIsDnsForced(data.getBoolean("isDnsForced"))
            .setIsSafeModeDisabled(data.getBoolean("isSafeModeDisabled"))
            .setIsFactoryResetBlocked(data.getBoolean("isFactoryResetBlocked"))
            .setIsTimeProtectionActive(data.getBoolean("isTimeProtectionActive"))
            .setCurrentState(FortressState.valueOf(data.getString("currentState")))
            .build()

        return IdentifierFortressPolicyBuilder.newBuilder()
            .setId(data.getString("id"))
            .setFortressPolicy(fortressPolicy)
            .build()
    }

    override fun reverseMap(data: IdentifierFortressPolicy): JSONObject {
        // Convert blocked apps list to JSON array
        val blockedAppsArray = JSONArray()
        data.getBlockedApps().forEach { blockedAppsArray.put(it) }

        return JSONObject().apply {
            put("id", data.getId())
            put("commitmentPlan", data.getCommitmentPlan().name)
            put("activationTimestamp", data.getActivationTimestamp())
            put("expiryTimestamp", data.getExpiryTimestamp())
            put("isDeviceOwnerActive", data.getIsDeviceOwnerActive())
            put("activationMethod", data.getActivationMethod().name)
            put("blockedApps", blockedAppsArray)
            put("isDnsForced", data.getIsDnsForced())
            put("isSafeModeDisabled", data.getIsSafeModeDisabled())
            put("isFactoryResetBlocked", data.getIsFactoryResetBlocked())
            put("isTimeProtectionActive", data.getIsTimeProtectionActive())
            put("currentState", data.getFortressState().name)
        }
    }
}