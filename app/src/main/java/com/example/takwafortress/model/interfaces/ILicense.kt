package com.example.takwafortress.model.interfaces

import com.example.takwafortress.model.enums.LicenseStatus


interface ILicense {
    fun getLicenseKey(): String
    fun getLicenseStatus(): LicenseStatus
    fun isValid(): Boolean
    fun canActivate(): Boolean
}