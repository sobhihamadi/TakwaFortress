package com.example.takwafortress.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.takwafortress.services.core.LicenseValidationService
import com.example.takwafortress.services.core.LicenseValidationResult
import com.example.takwafortress.util.crypto.HardwareIdGenerator
import com.example.takwafortress.util.exceptions.ValidationException
import kotlinx.coroutines.launch

class LicenseActivationViewModel(application: Application) : AndroidViewModel(application) {

    private val licenseValidationService = LicenseValidationService(application)

    // UI State
    private val _activationState = MutableLiveData<LicenseActivationState>()
    val activationState: LiveData<LicenseActivationState> = _activationState

    private val _hardwareId = MutableLiveData<String>()
    val hardwareId: LiveData<String> = _hardwareId

    init {
        loadHardwareId()
    }

    /**
     * Loads device hardware ID.
     */
    private fun loadHardwareId() {
        viewModelScope.launch {
            try {
                val hwid = HardwareIdGenerator.extractHardwareId(getApplication())
                _hardwareId.postValue(hwid)
            } catch (e: Exception) {
                _activationState.postValue(LicenseActivationState.Error("Failed to generate hardware ID"))
            }
        }
    }

    /**
     * Activates license with email and license key.
     */
    fun activateLicense(email: String, licenseKey: String) {
        viewModelScope.launch {
            try {
                // Validation (RELAXED FOR TESTING)
                if (email.isBlank()) {
                    throw ValidationException.InvalidEmail(email)
                }
                if (licenseKey.isBlank() || !licenseKey.startsWith("TAQWA-")) {
                    throw ValidationException.InvalidLicenseKeyFormat(licenseKey)
                }

                _activationState.postValue(LicenseActivationState.Loading)

                // Validate license
                val result = licenseValidationService.validateAndActivateLicense(email, licenseKey)

                when (result) {
                    is LicenseValidationResult.Success -> {
                        _activationState.postValue(
                            LicenseActivationState.Success(
                                email = email,
                                licenseKey = licenseKey
                            )
                        )
                    }
                    is LicenseValidationResult.InvalidLicenseKey -> {
                        _activationState.postValue(
                            LicenseActivationState.Error("Invalid license key. Please check and try again.")
                        )
                    }
                    is LicenseValidationResult.AlreadyBoundToAnotherDevice -> {
                        _activationState.postValue(
                            LicenseActivationState.Error("License already bound to another device. Each license works on one device only.")
                        )
                    }
                    is LicenseValidationResult.NetworkError -> {
                        _activationState.postValue(
                            LicenseActivationState.Error("Network error. Please check your internet connection.")
                        )
                    }
                }
            } catch (e: ValidationException) {
                _activationState.postValue(LicenseActivationState.Error(e.message ?: "Validation failed"))
            } catch (e: Exception) {
                _activationState.postValue(LicenseActivationState.Error("Unexpected error: ${e.message}"))
            }
        }
    }

    /**
     * Checks if user has valid license (called on app start).
     */
    fun checkExistingLicense() {
        viewModelScope.launch {
            try {
                val hasLicense = licenseValidationService.hasValidLicense()
                if (hasLicense) {
                    val user = licenseValidationService.getCurrentUser()
                    if (user != null) {
                        _activationState.postValue(
                            LicenseActivationState.AlreadyActivated(
                                email = user.email,
                                licenseKey = user.selectedPlan

                            )
                        )
                    }
                } else {
                    _activationState.postValue(LicenseActivationState.NotActivated)
                }
            } catch (e: Exception) {
                _activationState.postValue(LicenseActivationState.NotActivated)
            }
        }
    }

    /**
     * Validates license key format (RELAXED FOR TESTING).
     */
    fun validateLicenseKeyFormat(key: String): Boolean {
        // RELAXED: Just check if it starts with "TAQWA-" and has some content
        // This allows any length for testing
        return key.startsWith("TAQWA-") && key.length >= 10
    }

    /**
     * Validates email format (RELAXED FOR TESTING).
     */
    fun validateEmailFormat(email: String): Boolean {
        // RELAXED: Just check if it contains @ symbol
        return email.contains("@")
    }
}

sealed class LicenseActivationState {
    object NotActivated : LicenseActivationState()
    object Loading : LicenseActivationState()
    data class Success(val email: String, val licenseKey: String) : LicenseActivationState()
    data class AlreadyActivated(val email: String, val licenseKey: String) : LicenseActivationState()
    data class Error(val message: String) : LicenseActivationState()
}