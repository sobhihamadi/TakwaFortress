package com.example.takwafortress.model.builders

import com.example.takwafortress.model.entities.DeviceInfo
import com.example.takwafortress.model.entities.IdentifierDeviceInfo
import com.example.takwafortress.model.enums.ActivationMethod
import com.example.takwafortress.model.enums.DeviceBrand
import com.example.takwafortress.model.interfaces.ID

class DeviceInfoBuilder private constructor() {

    private var hardwareId: String? = null
    private var brand: DeviceBrand? = null
    private var model: String? = null
    private var androidVersion: Int? = null
    private var hasKnoxSupport: Boolean? = null
    private var activationMethod: ActivationMethod? = null
    private var firstInstallDate: Long? = null

    fun setHardwareId(hardwareId: String): DeviceInfoBuilder {
        this.hardwareId = hardwareId
        return this
    }

    fun setBrand(brand: DeviceBrand): DeviceInfoBuilder {
        this.brand = brand
        return this
    }

    fun setModel(model: String): DeviceInfoBuilder {
        this.model = model
        return this
    }

    fun setAndroidVersion(androidVersion: Int): DeviceInfoBuilder {
        this.androidVersion = androidVersion
        return this
    }

    fun setHasKnoxSupport(hasKnoxSupport: Boolean): DeviceInfoBuilder {
        this.hasKnoxSupport = hasKnoxSupport
        return this
    }

    fun setActivationMethod(activationMethod: ActivationMethod): DeviceInfoBuilder {
        this.activationMethod = activationMethod
        return this
    }

    fun setFirstInstallDate(firstInstallDate: Long): DeviceInfoBuilder {
        this.firstInstallDate = firstInstallDate
        return this
    }

    fun build(): DeviceInfo {
        require(hardwareId != null) { "Hardware ID is required" }
        require(brand != null) { "Brand is required" }
        require(model != null) { "Model is required" }
        require(androidVersion != null) { "Android version is required" }
        require(hasKnoxSupport != null) { "Knox support status is required" }
        require(activationMethod != null) { "Activation method is required" }
        require(firstInstallDate != null) { "First install date is required" }

        return DeviceInfo(
            hardwareId = hardwareId!!,
            brand = brand!!,
            model = model!!,
            androidVersion = androidVersion!!,
            hasKnoxSupport = hasKnoxSupport!!,
            activationMethod = activationMethod!!,
            firstInstallDate = firstInstallDate!!
        )
    }

    companion object {
        fun newBuilder(): DeviceInfoBuilder {
            return DeviceInfoBuilder()
        }
    }
}

class IdentifierDeviceInfoBuilder private constructor() {

    private var id: ID? = null
    private var deviceInfo: DeviceInfo? = null

    fun setId(id: ID): IdentifierDeviceInfoBuilder {
        require(id.isNotEmpty()) { "ID cannot be empty" }
        this.id = id
        return this
    }

    fun setDeviceInfo(deviceInfo: DeviceInfo): IdentifierDeviceInfoBuilder {
        this.deviceInfo = deviceInfo
        return this
    }

    fun build(): IdentifierDeviceInfo {
        require(id != null) { "ID is required to build IdentifierDeviceInfo" }
        require(deviceInfo != null) { "DeviceInfo is required to build IdentifierDeviceInfo" }

        val di = deviceInfo!!

        return IdentifierDeviceInfo(
            id = id!!,
            hardwareId = di.getHardwareId(),
            brand = di.getBrand(),
            model = di.getModel(),
            androidVersion = di.getAndroidVersion(),
            hasKnoxSupport = di.getHasKnoxSupport(),
            activationMethod = di.getActivationMethod(),
            firstInstallDate = di.getFirstInstallDate()
        )
    }

    companion object {
        fun newBuilder(): IdentifierDeviceInfoBuilder {
            return IdentifierDeviceInfoBuilder()
        }
    }
}