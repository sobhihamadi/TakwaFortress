package com.example.takwafortress.model.entities

import com.example.takwafortress.model.enums.LicenseStatus
import com.example.takwafortress.model.interfaces.ID
import com.example.takwafortress.model.interfaces.IIdentifiable
import com.example.takwafortress.model.interfaces.ILicense


open class License(
    private val licenseKey: String,
    private val userId: String,
    private val deviceId: String,
    private val isActive: Boolean,
    private val activationDate: Long,
    private val isRevoked: Boolean,
    private val revokeReason: String?
) : ILicense {

    override fun getLicenseKey(): String = licenseKey

    fun getUserId(): String = userId

    fun getDeviceId(): String = deviceId

    fun getIsActive(): Boolean = isActive

    fun getActivationDate(): Long = activationDate

    fun getIsRevoked(): Boolean = isRevoked

    fun getRevokeReason(): String? = revokeReason

    override fun getLicenseStatus(): LicenseStatus {
        return when {
            isRevoked -> LicenseStatus.REVOKED
            isActive -> LicenseStatus.ACTIVE
            else -> LicenseStatus.NONE
        }
    }

    override fun isValid(): Boolean {
        return isActive && !isRevoked
    }

    override fun canActivate(): Boolean {
        return isValid()
    }

    fun canBindToDevice(hardwareId: String): Boolean {
        return deviceId.isEmpty() || deviceId == hardwareId
    }

    fun isBoundToDevice(hardwareId: String): Boolean {
        return deviceId == hardwareId
    }
}

class IdentifierLicense(
    private val id: ID,
    licenseKey: String,
    userId: String,
    deviceId: String,
    isActive: Boolean,
    activationDate: Long,
    isRevoked: Boolean,
    revokeReason: String?
) : License(
    licenseKey,
    userId,
    deviceId,
    isActive,
    activationDate,
    isRevoked,
    revokeReason
), IIdentifiable {

    override fun getId(): ID = id
}
