package com.example.takwafortress.mappers

import com.example.takwafortress.model.builders.IdentifierLicenseBuilder
import com.example.takwafortress.model.builders.LicenseBuilder
import com.example.takwafortress.model.entities.IdentifierLicense


import org.json.JSONObject

class LicenseMapper : IMapper<JSONObject, IdentifierLicense> {

    override fun map(data: JSONObject): IdentifierLicense {
        val license = LicenseBuilder.newBuilder()
            .setLicenseKey(data.getString("licenseKey"))
            .setUserId(data.getString("userId"))
            .setDeviceId(data.getString("deviceId"))
            .setIsActive(data.getBoolean("isActive"))
            .setActivationDate(data.getLong("activationDate"))
            .setIsRevoked(data.getBoolean("isRevoked"))
            .setRevokeReason(data.optString("revokeReason", null))
            .build()

        return IdentifierLicenseBuilder.newBuilder()
            .setId(data.getString("id"))
            .setLicense(license)
            .build()
    }

    override fun reverseMap(data: IdentifierLicense): JSONObject {
        return JSONObject().apply {
            put("id", data.getId())
            put("licenseKey", data.getLicenseKey())
            put("userId", data.getUserId())
            put("deviceId", data.getDeviceId())
            put("isActive", data.getIsActive())
            put("activationDate", data.getActivationDate())
            put("isRevoked", data.getIsRevoked())
            put("revokeReason", data.getRevokeReason() ?: JSONObject.NULL)
        }
    }
}