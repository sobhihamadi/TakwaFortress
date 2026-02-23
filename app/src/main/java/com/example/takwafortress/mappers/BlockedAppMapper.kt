package com.example.takwafortress.mappers

import com.example.takwafortress.model.builders.BlockedAppBuilder
import com.example.takwafortress.model.builders.IdentifierBlockedAppBuilder
import com.example.takwafortress.model.entities.IdentifierBlockedApp
import org.json.JSONObject

class BlockedAppMapper : IMapper<JSONObject, IdentifierBlockedApp> {

    override fun map(data: JSONObject): IdentifierBlockedApp {
        // ✅ NEW: Handle new fields with backward compatibility
        val isInstalled = data.optBoolean("isInstalled", true)
        val isPreBlocked = data.optBoolean("isPreBlocked", false)

        val blockedApp = BlockedAppBuilder.newBuilder()
            .setPackageName(data.getString("packageName"))
            .setAppName(data.getString("appName"))
            .setIsSystemApp(data.getBoolean("isSystemApp"))
            .setIsSuspended(data.getBoolean("isSuspended"))
            .setBlockReason(data.getString("blockReason"))
            .setDetectedDate(data.getLong("detectedDate"))
            .setIsInstalled(isInstalled)  // ✅ NEW
            .setIsPreBlocked(isPreBlocked)  // ✅ NEW
            .build()

        return IdentifierBlockedAppBuilder.newBuilder()
            .setId(data.getString("id"))
            .setBlockedApp(blockedApp)
            .build()
    }

    override fun reverseMap(data: IdentifierBlockedApp): JSONObject {
        return JSONObject().apply {
            put("id", data.getId())
            put("packageName", data.getPackageName())
            put("appName", data.getAppName())
            put("isSystemApp", data.getIsSystemApp())
            put("isSuspended", data.getIsSuspended())
            put("blockReason", data.getBlockReason())
            put("detectedDate", data.getDetectedDate())
            put("isInstalled", data.getIsInstalled())  // ✅ NEW
            put("isPreBlocked", data.getIsPreBlocked())  // ✅ NEW
        }
    }
}