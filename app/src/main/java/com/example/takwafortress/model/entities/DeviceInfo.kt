
package com.example.takwafortress.model.entities

import com.example.takwafortress.model.enums.ActivationMethod
import com.example.takwafortress.model.enums.DeviceBrand
import com.example.takwafortress.model.interfaces.ID
import com.example.takwafortress.model.interfaces.IIdentifiable



open class DeviceInfo(
    private val hardwareId: String,
    private val brand: DeviceBrand,
    private val model: String,
    private val androidVersion: Int,
    private val hasKnoxSupport: Boolean,
    private val activationMethod: ActivationMethod,
    private val firstInstallDate: Long
) {

    fun getHardwareId(): String = hardwareId

    fun getBrand(): DeviceBrand = brand

    fun getModel(): String = model

    fun getAndroidVersion(): Int = androidVersion

    fun getHasKnoxSupport(): Boolean = hasKnoxSupport

    fun getActivationMethod(): ActivationMethod = activationMethod

    fun getFirstInstallDate(): Long = firstInstallDate

    fun supportsKnoxActivation(): Boolean {
        return brand == DeviceBrand.SAMSUNG && hasKnoxSupport
    }

    fun requiresWirelessAdb(): Boolean {
        return !supportsKnoxActivation()
    }

    fun requiresEmailRemoval(): Boolean {
        return activationMethod == ActivationMethod.WIRELESS_ADB
    }

    fun getSetupTime(): String {
        return brand.setupTime
    }

    fun getRecommendedActivationMethod(): ActivationMethod {
        return if (supportsKnoxActivation()) {
            ActivationMethod.KNOX
        } else {
            ActivationMethod.WIRELESS_ADB
        }
    }

    fun isAndroidVersionSupported(): Boolean {
        // Taqwa requires Android 8.0+ (API 26+)
        return androidVersion >= 26
    }

    fun getAndroidVersionName(): String {
        return when (androidVersion) {
            26, 27 -> "Android 8.0 Oreo"
            28 -> "Android 9.0 Pie"
            29 -> "Android 10"
            30 -> "Android 11"
            31 -> "Android 12"
            32 -> "Android 12L"
            33 -> "Android 13"
            34 -> "Android 14"
            35 -> "Android 15"
            else -> "Android $androidVersion"
        }
    }

    fun wasReinstalled(currentInstallDate: Long): Boolean {
        // If current install date is AFTER the stored first install date,
        // it means the app was uninstalled and reinstalled (factory reset)
        return currentInstallDate > firstInstallDate
    }
}

class IdentifierDeviceInfo(
    private val id: ID,
    hardwareId: String,
    brand: DeviceBrand,
    model: String,
    androidVersion: Int,
    hasKnoxSupport: Boolean,
    activationMethod: ActivationMethod,
    firstInstallDate: Long
) : DeviceInfo(
    hardwareId,
    brand,
    model,
    androidVersion,
    hasKnoxSupport,
    activationMethod,
    firstInstallDate
), IIdentifiable {

    override fun getId(): ID = id
}