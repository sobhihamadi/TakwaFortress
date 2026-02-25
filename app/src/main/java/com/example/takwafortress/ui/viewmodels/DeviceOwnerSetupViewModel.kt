package com.example.takwafortress.ui.viewmodels

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.takwafortress.model.builders.IdentifierUserBuilder
import com.example.takwafortress.model.builders.UserBuilder
import com.example.takwafortress.model.enums.ActivationMethod
import com.example.takwafortress.model.enums.DeviceBrand
import com.example.takwafortress.repository.implementations.FirebaseUserRepository
import com.example.takwafortress.services.core.DeviceOwnerService
import com.example.takwafortress.services.security.KnoxActivationService
import com.example.takwafortress.services.security.WirelessAdbService
import com.example.takwafortress.services.security.WirelessAdbResult
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class DeviceOwnerSetupViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "DeviceOwnerSetupVM"
    }

    private val deviceOwnerService = DeviceOwnerService(application)
    private val knoxService        = KnoxActivationService(application)
    private val wirelessAdbService = WirelessAdbService(application)
    private val userRepository     = FirebaseUserRepository(application)
    private val auth               = FirebaseAuth.getInstance()

    private val _setupState = MutableLiveData<DeviceOwnerSetupState>()
    val setupState: LiveData<DeviceOwnerSetupState> = _setupState

    private val _deviceBrand = MutableLiveData<DeviceBrand>()
    val deviceBrand: LiveData<DeviceBrand> = _deviceBrand

    private val _activationMethod = MutableLiveData<ActivationMethod>()
    val activationMethod: LiveData<ActivationMethod> = _activationMethod

    init {
        detectDeviceAndMethod()
    }

    private fun detectDeviceAndMethod() {
        val brand = DeviceBrand.fromManufacturer(Build.MANUFACTURER)
        _deviceBrand.postValue(brand)

        // ✅ MVP: Always use Wireless ADB for all devices
        // Knox activation disabled until Samsung developer account is set up
        val method = ActivationMethod.WIRELESS_ADB
        _activationMethod.postValue(method)


        _setupState.postValue(DeviceOwnerSetupState.Ready(brand, method))
    }

    fun checkDeviceOwnerStatus() {
        viewModelScope.launch {
            try {
                if (deviceOwnerService.isDeviceOwner()) {
                    // Device Owner already active — make sure Firestore reflects this
                    saveHasDeviceOwnerToFirestore()
                    _setupState.postValue(DeviceOwnerSetupState.AlreadyActive)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking device owner status", e)
            }
        }
    }

    fun activateViaKnox() {
        viewModelScope.launch {
            try {
                _setupState.postValue(DeviceOwnerSetupState.Activating("Activating via Knox…"))

                when (val result = knoxService.activateViaKnox()) {
                    is com.example.takwafortress.services.security.KnoxActivationResult.Success -> {
                        saveHasDeviceOwnerToFirestore()
                        _setupState.postValue(DeviceOwnerSetupState.Success)
                    }
                    is com.example.takwafortress.services.security.KnoxActivationResult.NotSupported ->
                        _setupState.postValue(DeviceOwnerSetupState.Error("Knox not supported"))
                    is com.example.takwafortress.services.security.KnoxActivationResult.Failed ->
                        _setupState.postValue(DeviceOwnerSetupState.Error("Knox failed: ${result.error}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Knox activation crashed", e)
                _setupState.postValue(DeviceOwnerSetupState.Error("Knox crashed: ${e.message}"))
            }
        }
    }

    fun activateViaWirelessAdb(pairingCode: String, pairingPort: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "═══════════════════════════════════════")
                Log.d(TAG, "WIRELESS ADB ACTIVATION STARTED (background)")
                Log.d(TAG, "Code: ${pairingCode.take(2)}****  Port: $pairingPort")

                // ── Validation ──────────────────────────────────────────────
                if (pairingCode.length != 6 || !pairingCode.all { it.isDigit() }) {
                    _setupState.postValue(DeviceOwnerSetupState.Error(
                        "❌ Invalid code — must be exactly 6 digits. Got: '$pairingCode'"
                    ))
                    return@launch
                }

                val port = pairingPort.toIntOrNull()
                if (port == null || port !in 1024..65535) {
                    _setupState.postValue(DeviceOwnerSetupState.Error(
                        "❌ Invalid port: $pairingPort"
                    ))
                    return@launch
                }

                // ── Step 1: Pair (runs fully in background) ─────────────────
                // We do NOT post Activating state here so the UI stays quiet
                // and the user stays on the Settings popup screen undisturbed.
                Log.d(TAG, "Starting silent background pairing…")

                val pairResult = wirelessAdbService.pairDevice(pairingCode, port)

                when (pairResult) {
                    is WirelessAdbResult.Failed -> {
                        Log.e(TAG, "❌ Pairing failed: ${pairResult.reason}")
                        _setupState.postValue(DeviceOwnerSetupState.Error(pairResult.reason))
                        return@launch
                    }
                    is WirelessAdbResult.PairingSuccessNeedConnection -> {
                        Log.d(TAG, "✅ Pairing successful, connecting…")
                    }
                    else -> {
                        _setupState.postValue(DeviceOwnerSetupState.Error("Unexpected pairing result"))
                        return@launch
                    }
                }

                // ── Step 2: Connect + set Device Owner ──────────────────────
                // Still silent — user is still on the Settings screen
                Log.d(TAG, "Connecting and executing dpm command…")

                val execResult = wirelessAdbService.connectAndSetDeviceOwner()

                when (execResult) {
                    is WirelessAdbResult.Success -> {
                        Log.d(TAG, "✅ DEVICE OWNER ACTIVATED!")
                        saveHasDeviceOwnerToFirestore()
                        // NOW we surface — bring app forward with success
                        _setupState.postValue(DeviceOwnerSetupState.Success)
                    }

                    is WirelessAdbResult.AccountsExist -> {
                        _setupState.postValue(DeviceOwnerSetupState.Error(
                            "❌ Accounts still exist\n\n" +
                                    "Remove ALL accounts first:\n" +
                                    "Settings → Accounts → Remove each one\n" +
                                    "Then come back and tap the button again"
                        ))
                    }

                    is WirelessAdbResult.Failed -> {
                        val reason = execResult.reason
                        if (reason.contains("already", ignoreCase = true)) {
                            Log.d(TAG, "✅ Device Owner already set — treating as success")
                            saveHasDeviceOwnerToFirestore()
                            _setupState.postValue(DeviceOwnerSetupState.Success)
                        } else {
                            _setupState.postValue(DeviceOwnerSetupState.Error(reason))
                        }
                    }

                    else -> _setupState.postValue(DeviceOwnerSetupState.Error("Unexpected result"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Activation crashed: ${e.message}", e)
                _setupState.postValue(DeviceOwnerSetupState.Error(
                    "❌ Activation crashed\n\nError: ${e.message}"
                ))
            }
        }
    }

    fun onActivationSuccessFromService() {
        viewModelScope.launch {
            saveHasDeviceOwnerToFirestore()
            _setupState.postValue(DeviceOwnerSetupState.Success)
        }
    }

    fun onActivationErrorFromService(error: String) {
        _setupState.postValue(DeviceOwnerSetupState.Error(error))
    }

    /**
     * Saves hasDeviceOwner = true to Firestore.
     * Called after any successful activation path.
     */
    private suspend fun saveHasDeviceOwnerToFirestore() {
        try {
            val userId = auth.currentUser?.uid ?: run {
                Log.e(TAG, "No authenticated user — cannot save hasDeviceOwner")
                return
            }

            val existingUser = userRepository.get(userId)

            val updatedUser = UserBuilder.newBuilder()
                .setEmail(existingUser.email)
                .setDeviceId(existingUser.deviceId)
                .setSubscriptionStatus(existingUser.subscriptionStatus)
                .setHasDeviceOwner(true)          // ✅ THE KEY UPDATE
                .setSelectedPlan(existingUser.selectedPlan)
                .setCommitmentDays(existingUser.commitmentDays)
                .setCommitmentStartDate(existingUser.commitmentStartDate)
                .setCommitmentEndDate(existingUser.commitmentEndDate)
                .setCreatedAt(existingUser.createdAt)
                .setUpdatedAt(Timestamp.now())
                .build()

            val updatedIdentifierUser = IdentifierUserBuilder
                .fromUser(userId, updatedUser)
                .build()

            userRepository.setCurrentUser(updatedIdentifierUser)

            Log.d(TAG, "✅ hasDeviceOwner = true saved to Firestore")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save hasDeviceOwner to Firestore: ${e.message}")
            // Don't crash — the Device Owner is still set on the device even if Firestore fails
        }
    }

    fun getSetupInstructions(): String = when (_activationMethod.value) {
        ActivationMethod.KNOX         -> knoxService.getActivationInstructions()
        ActivationMethod.WIRELESS_ADB -> wirelessAdbService.getPairingInstructions()
        else                          -> "Unknown method"
    }

    fun isWirelessDebuggingEnabled(): Boolean = wirelessAdbService.isWirelessDebuggingEnabled()
}

sealed class DeviceOwnerSetupState {
    data class Ready(val brand: DeviceBrand, val method: ActivationMethod) : DeviceOwnerSetupState()
    data class Activating(val message: String) : DeviceOwnerSetupState()
    object Success       : DeviceOwnerSetupState()
    object AlreadyActive : DeviceOwnerSetupState()
    data class Error(val message: String) : DeviceOwnerSetupState()
}