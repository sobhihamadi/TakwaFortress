package com.example.takwafortress.model.builders

import com.example.takwafortress.model.entities.IdentifierLicense
import com.example.takwafortress.model.entities.License
import com.example.takwafortress.model.interfaces.ID


class LicenseBuilder private constructor() {

    private var licenseKey: String? = null
    private var userId: String? = null
    private var deviceId: String? = null
    private var isActive: Boolean? = null
    private var activationDate: Long? = null
    private var isRevoked: Boolean? = null
    private var revokeReason: String? = null

    fun setLicenseKey(licenseKey: String): LicenseBuilder {
        this.licenseKey = licenseKey
        return this
    }

    fun setUserId(userId: String): LicenseBuilder {
        this.userId = userId
        return this
    }

    fun setDeviceId(deviceId: String): LicenseBuilder {
        this.deviceId = deviceId
        return this
    }

    fun setIsActive(isActive: Boolean): LicenseBuilder {
        this.isActive = isActive
        return this
    }

    fun setActivationDate(activationDate: Long): LicenseBuilder {
        this.activationDate = activationDate
        return this
    }

    fun setIsRevoked(isRevoked: Boolean): LicenseBuilder {
        this.isRevoked = isRevoked
        return this
    }

    fun setRevokeReason(revokeReason: String?): LicenseBuilder {
        this.revokeReason = revokeReason
        return this
    }

    fun build(): License {
        require(licenseKey != null) { "License key is required" }
        require(userId != null) { "User ID is required" }
        require(deviceId != null) { "Device ID is required" }
        require(isActive != null) { "Active status is required" }
        require(activationDate != null) { "Activation date is required" }
        require(isRevoked != null) { "Revoked status is required" }

        return License(
            licenseKey = licenseKey!!,
            userId = userId!!,
            deviceId = deviceId!!,
            isActive = isActive!!,
            activationDate = activationDate!!,
            isRevoked = isRevoked!!,
            revokeReason = revokeReason
        )
    }

    companion object {
        fun newBuilder(): LicenseBuilder {
            return LicenseBuilder()
        }
    }
}

class IdentifierLicenseBuilder private constructor() {

    private var id: ID? = null
    private var license: License? = null

    fun setId(id: ID): IdentifierLicenseBuilder {
        require(id.isNotEmpty()) { "ID cannot be empty" }
        this.id = id
        return this
    }

    fun setLicense(license: License): IdentifierLicenseBuilder {
        this.license = license
        return this
    }

    fun build(): IdentifierLicense {
        require(id != null) { "ID is required to build IdentifierLicense" }
        require(license != null) { "License is required to build IdentifierLicense" }

        val l = license!!

        return IdentifierLicense(
            id = id!!,
            licenseKey = l.getLicenseKey(),
            userId = l.getUserId(),
            deviceId = l.getDeviceId(),
            isActive = l.getIsActive(),
            activationDate = l.getActivationDate(),
            isRevoked = l.getIsRevoked(),
            revokeReason = l.getRevokeReason()
        )
    }

    companion object {
        fun newBuilder(): IdentifierLicenseBuilder {
            return IdentifierLicenseBuilder()
        }
    }
}