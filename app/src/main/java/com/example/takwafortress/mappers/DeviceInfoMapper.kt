package com.example.takwafortress.mappers

import com.example.takwafortress.model.builders.DeviceInfoBuilder
import com.example.takwafortress.model.builders.IdentifierDeviceInfoBuilder
import com.example.takwafortress.model.entities.IdentifierDeviceInfo
import com.example.takwafortress.model.enums.ActivationMethod
import com.example.takwafortress.model.enums.DeviceBrand


import org.json.JSONObject

class DeviceInfoMapper : IMapper<JSONObject, IdentifierDeviceInfo> {

    override fun map(data: JSONObject): IdentifierDeviceInfo {
        val deviceInfo = DeviceInfoBuilder.newBuilder()
            .setHardwareId(data.getString("hardwareId"))
            .setBrand(DeviceBrand.valueOf(data.getString("brand")))
            .setModel(data.getString("model"))
            .setAndroidVersion(data.getInt("androidVersion"))
            .setHasKnoxSupport(data.getBoolean("hasKnoxSupport"))
            .setActivationMethod(ActivationMethod.valueOf(data.getString("activationMethod")))
            .setFirstInstallDate(data.getLong("firstInstallDate"))
            .build()

        return IdentifierDeviceInfoBuilder.newBuilder()
            .setId(data.getString("id"))
            .setDeviceInfo(deviceInfo)
            .build()
    }

    override fun reverseMap(data: IdentifierDeviceInfo): JSONObject {
        return JSONObject().apply {
            put("id", data.getId())
            put("hardwareId", data.getHardwareId())
            put("brand", data.getBrand().name)
            put("model", data.getModel())
            put("androidVersion", data.getAndroidVersion())
            put("hasKnoxSupport", data.getHasKnoxSupport())
            put("activationMethod", data.getActivationMethod().name)
            put("firstInstallDate", data.getFirstInstallDate())
        }
    }
}