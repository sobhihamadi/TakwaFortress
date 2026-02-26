package com.example.takwafortress.ui.viewmodels

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import com.example.takwafortress.services.security.AdbDiscoveryService
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

        val method = ActivationMethod.WIRELESS_ADB
        _activationMethod.postValue(method)

        _setupState.postValue(DeviceOwnerSetupState.Ready(brand, method))
    }

    fun checkDeviceOwnerStatus() {
        viewModelScope.launch {
            try {
                if (deviceOwnerService.isDeviceOwner()) {
                    saveHasDeviceOwnerToFirestore()
                    _setupState.postValue(DeviceOwnerSetupState.AlreadyActive)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking device owner status", e)
            }
        }
    }

    // ── mDNS discovery control ────────────────────────────────────────────────

    /**
     * Starts the AdbDiscoveryService as a foreground service.
     * Call this when the setup screen becomes visible.
     */
    fun startDiscovery() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, AdbDiscoveryService::class.java)
        ctx.startForegroundService(intent)
        Log.d(TAG, "AdbDiscoveryService started")

        // Show "waiting" state so the UI reflects that discovery is running
        _setupState.postValue(
            DeviceOwnerSetupState.WaitingForPairing(
                "Open Settings → Developer Options → Wireless Debugging\n\n" +
                        "Then tap \"Pair device with pairing code\".\n\n" +
                        "A notification will appear here — enter the code there.\n\n" +
                        "⚠️ Remove ALL accounts first!"
            )
        )
    }

    /**
     * Stops the AdbDiscoveryService.
     * Delayed 600ms so Android has time to finish onStartCommand + startForeground()
     * before we stop — prevents ForegroundServiceDidNotStartInTimeException crash.
     */
    fun stopDiscovery() {
        val ctx = getApplication<Application>()
        Handler(Looper.getMainLooper()).postDelayed({
            ctx.stopService(Intent(ctx, AdbDiscoveryService::class.java))
            Log.d(TAG, "AdbDiscoveryService stopped")
        }, 600)
    }

    /**
     * Called from the Activity's BroadcastReceiver when NotificationReplyReceiver
     * broadcasts the ADB result back to the UI.
     */
    fun receiveAdbResult(success: Boolean, errorMessage: String) {
        viewModelScope.launch {
            if (success) {
                Log.d(TAG, "✅ Received success from NotificationReplyReceiver")
                saveHasDeviceOwnerToFirestore()
                _setupState.postValue(DeviceOwnerSetupState.Success)
            } else {
                Log.e(TAG, "❌ Received error from NotificationReplyReceiver: $errorMessage")
                _setupState.postValue(DeviceOwnerSetupState.Error(errorMessage))
            }
        }
    }

    // ── Knox (unchanged) ─────────────────────────────────────────────────────

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

    // ── Firestore ─────────────────────────────────────────────────────────────

    fun saveDeviceOwnerToFirestore() {
        viewModelScope.launch { saveHasDeviceOwnerToFirestore() }
    }

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
                .setHasDeviceOwner(true)
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
            Log.e(TAG, "❌ Failed to save hasDeviceOwner: ${e.message}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun getSetupInstructions(): String = when (_activationMethod.value) {
        ActivationMethod.KNOX         -> knoxService.getActivationInstructions()
        ActivationMethod.WIRELESS_ADB -> wirelessAdbService.getPairingInstructions()
        else                          -> "Unknown method"
    }

    fun isWirelessDebuggingEnabled(): Boolean = wirelessAdbService.isWirelessDebuggingEnabled()
}

// ── State ─────────────────────────────────────────────────────────────────────

sealed class DeviceOwnerSetupState {
    data class Ready(val brand: DeviceBrand, val method: ActivationMethod) : DeviceOwnerSetupState()
    data class WaitingForPairing(val instructions: String) : DeviceOwnerSetupState()
    data class Activating(val message: String) : DeviceOwnerSetupState()
    object Success       : DeviceOwnerSetupState()
    object AlreadyActive : DeviceOwnerSetupState()
    data class Error(val message: String) : DeviceOwnerSetupState()
}