package com.example.takwafortress.ui.viewmodels

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.takwafortress.repository.implementations.FirebaseUserRepository
import com.example.takwafortress.services.core.DeviceOwnerService
import com.example.takwafortress.services.filtering.ContentFilteringService
import com.example.takwafortress.util.constants.BlockedPackages
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────
// Data classes
// ─────────────────────────────────────────────

data class AppItem(
    val packageName: String,
    val displayName: String,
    val category: AppCategory,
    val isInstalled: Boolean,
    var isBlocked: Boolean
)

enum class AppCategory { BROWSER, NUCLEAR, OTHER }

data class ProtectionReport(
    val isDnsActive: Boolean,
    val suspendedBrowsers: Int,
    val totalBrowsers: Int,
    val hiddenNuclearApps: Int,
    val totalNuclearApps: Int,
    val totalBlockedApps: Int
)

data class RemainingTime(
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long
) {
    val formatted: String
        get() = "%02d : %02d : %02d : %02d".format(days, hours, minutes, seconds)
}

data class FortressSettings(
    var isFactoryResetBlocked: Boolean = false,
    var isDeveloperModeBlocked: Boolean = false,
    var isAutoTimeEnabled: Boolean = false,
    var isDnsFilterActive: Boolean = true
)

// ─────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────

class FortressStatusViewModel(private val context: Context) : ViewModel() {

    private val deviceOwnerService      = DeviceOwnerService(context)
    private val contentFilteringService = ContentFilteringService(context)
    private val packageManager          = context.packageManager
    private val repository              = com.example.takwafortress.repository.implementations.LocalBlockedAppRepository(context)
    private val userRepository          = FirebaseUserRepository(context)
    private val auth                    = FirebaseAuth.getInstance()
    private val devicePolicyManager     = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent          = android.content.ComponentName(
        context, com.example.takwafortress.receivers.DeviceAdminReceiver::class.java
    )

    // ── LiveData ──
    val appList: LiveData<MutableList<AppItem>>      get() = _appList
    val protectionReport: LiveData<ProtectionReport> get() = _protectionReport
    val remainingTime: LiveData<RemainingTime>       get() = _remainingTime
    val settings: LiveData<FortressSettings>         get() = _settings
    val progressPercent: LiveData<Int>               get() = _progressPercent
    val planName: LiveData<String>                   get() = _planName
    val toastMessage: LiveData<String>               get() = _toastMessage
    val isCommitmentExpired: LiveData<Boolean>       get() = _isCommitmentExpired

    private val _appList             = MutableLiveData<MutableList<AppItem>>()
    private val _protectionReport    = MutableLiveData<ProtectionReport>()
    private val _remainingTime       = MutableLiveData<RemainingTime>()
    private val _settings            = MutableLiveData<FortressSettings>()
    private val _progressPercent     = MutableLiveData<Int>()
    private val _planName            = MutableLiveData<String>()
    private val _toastMessage        = MutableLiveData<String>()
    private val _isCommitmentExpired = MutableLiveData<Boolean>(false)

    private var countdownJob: kotlinx.coroutines.Job? = null
    private var autoTimeState: Boolean = false

    private var commitmentEndTimestamp: Long   = 0L
    private var commitmentStartTimestamp: Long = 0L
    private var commitmentDays: Int            = 0

    init {
        loadSettings()
        loadAppList()
        loadCommitmentFromFirestore()
    }

    // ═══════════════════════════════════════════
    // Settings
    // ✅ FIX: moved getProtectionStatus() to IO dispatcher —
    // it reads from SharedPreferences/DPM which can block main thread
    // ═══════════════════════════════════════════

    fun loadSettings() {
        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) {
                val protectionStatus = contentFilteringService.getProtectionStatus()
                FortressSettings(
                    isFactoryResetBlocked  = hasUserRestriction(android.os.UserManager.DISALLOW_FACTORY_RESET),
                    isDeveloperModeBlocked = hasUserRestriction(android.os.UserManager.DISALLOW_DEBUGGING_FEATURES),
                    isAutoTimeEnabled      = autoTimeState,
                    isDnsFilterActive      = protectionStatus.dnsFilterActive
                )
            }
            _settings.postValue(settings)
        }
    }

    // ═══════════════════════════════════════════
    // Firestore commitment loader
    // ═══════════════════════════════════════════

    fun loadCommitmentFromFirestore() {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: run {
                    _remainingTime.postValue(RemainingTime(0, 0, 0, 0))
                    _planName.postValue("No active plan")
                    _isCommitmentExpired.postValue(false)
                    return@launch
                }

                val user = userRepository.get(userId)

                commitmentEndTimestamp   = user.commitmentEndDate?.toDate()?.time ?: 0L
                commitmentStartTimestamp = user.commitmentStartDate?.toDate()?.time ?: 0L
                commitmentDays           = user.commitmentDays ?: 0

                _planName.postValue(buildPlanName(user.selectedPlan, commitmentDays))

                val nowMs   = System.currentTimeMillis()
                val expired = commitmentEndTimestamp > 0L && nowMs >= commitmentEndTimestamp
                _isCommitmentExpired.postValue(expired)

                startCountdown()

            } catch (e: Exception) {
                android.util.Log.e("FortressStatusVM", "Firestore load failed: ${e.message}")
                _remainingTime.postValue(RemainingTime(0, 0, 0, 0))
                _planName.postValue("Plan unavailable")
                _isCommitmentExpired.postValue(false)
            }
        }
    }

    private fun buildPlanName(selectedPlan: String?, days: Int): String {
        return when {
            selectedPlan == "TRIAL_3" -> "3-Day Free Trial"
            days == 30               -> "30-Day Commitment"
            days == 90               -> "90-Day Brain Reset"
            days == 180              -> "180-Day Stability"
            days == 365              -> "365-Day Transformation"
            days > 0                 -> "${days}-Day Plan"
            else                     -> "Active Plan"
        }
    }

    // ═══════════════════════════════════════════
    // Countdown
    // ═══════════════════════════════════════════

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val nowMs       = System.currentTimeMillis()
                val remainingMs = commitmentEndTimestamp - nowMs

                if (remainingMs <= 0 || commitmentEndTimestamp == 0L) {
                    _remainingTime.postValue(RemainingTime(0, 0, 0, 0))
                    _progressPercent.postValue(100)
                    _isCommitmentExpired.postValue(true)
                    break
                }

                val totalSeconds = remainingMs / 1000
                _remainingTime.postValue(
                    RemainingTime(
                        days    = totalSeconds / 86400,
                        hours   = (totalSeconds % 86400) / 3600,
                        minutes = (totalSeconds % 3600) / 60,
                        seconds = totalSeconds % 60
                    )
                )

                val totalMs   = commitmentEndTimestamp - commitmentStartTimestamp
                val elapsedMs = nowMs - commitmentStartTimestamp
                if (totalMs > 0) {
                    _progressPercent.postValue(
                        ((elapsedMs.toDouble() / totalMs.toDouble()) * 100)
                            .toInt().coerceIn(0, 100)
                    )
                }

                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    // ═══════════════════════════════════════════
    // App list
    // ✅ FIX: all PackageManager and DPM calls moved to IO dispatcher
    // ═══════════════════════════════════════════

    fun loadAppList() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                val result = mutableListOf<AppItem>()
                for ((pkg, name) in BlockedPackages.BROWSERS) {
                    if (!isAppInstalled(pkg)) continue
                    result.add(AppItem(pkg, name, AppCategory.BROWSER, true, isAppSuspendedOrHidden(pkg)))
                }
                for ((pkg, name) in BlockedPackages.NUCLEAR_BLACKLIST) {
                    if (!isAppInstalled(pkg)) continue
                    result.add(AppItem(pkg, name, AppCategory.NUCLEAR, true, isAppSuspendedOrHidden(pkg)))
                }
                result
            }
            _appList.postValue(list)
            rebuildProtectionReport(list)
        }
    }

    fun toggleAppBlock(packageName: String, shouldBlock: Boolean) {
        viewModelScope.launch {
            val list = _appList.value ?: return@launch
            val item = list.find { it.packageName == packageName } ?: return@launch
            val success = withContext(Dispatchers.IO) {
                if (shouldBlock) blockApp(packageName, item.category)
                else             unblockApp(packageName, item.category)
            }
            if (success) {
                item.isBlocked = shouldBlock
                _appList.postValue(list)
                rebuildProtectionReport(list)
                _toastMessage.postValue(if (shouldBlock) "${item.displayName} blocked" else "${item.displayName} unblocked")
            } else {
                _toastMessage.postValue("Failed to change ${item.displayName}")
            }
        }
    }

    // ═══════════════════════════════════════════
    // Settings toggles
    // ═══════════════════════════════════════════

    fun toggleFactoryResetBlock(enable: Boolean) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (enable) addRestriction(android.os.UserManager.DISALLOW_FACTORY_RESET)
                else        removeRestriction(android.os.UserManager.DISALLOW_FACTORY_RESET)
            }
            if (ok) {
                _settings.postValue(_settings.value?.copy(isFactoryResetBlocked = enable) ?: return@launch)
                _toastMessage.postValue(if (enable) "Factory reset blocked" else "Factory reset allowed")
            } else {
                _toastMessage.postValue("Failed – Device Owner required")
            }
        }
    }

    fun toggleDeveloperModeBlock(enable: Boolean) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (enable) addRestriction(android.os.UserManager.DISALLOW_DEBUGGING_FEATURES)
                else        removeRestriction(android.os.UserManager.DISALLOW_DEBUGGING_FEATURES)
            }
            if (ok) {
                _settings.postValue(_settings.value?.copy(isDeveloperModeBlocked = enable) ?: return@launch)
                _toastMessage.postValue(if (enable) "Developer mode blocked" else "Developer mode allowed")
            } else {
                _toastMessage.postValue("Failed – Device Owner required")
            }
        }
    }

    fun toggleAutoTime(enable: Boolean) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    if (!deviceOwnerService.isDeviceOwner()) false
                    else {
                        devicePolicyManager.setAutoTimeRequired(adminComponent, enable)
                        autoTimeState = enable
                        true
                    }
                } catch (e: Exception) { false }
            }
            if (ok) {
                _settings.postValue(_settings.value?.copy(isAutoTimeEnabled = enable) ?: return@launch)
                _toastMessage.postValue(if (enable) "Auto time enabled" else "Auto time disabled")
            } else {
                _toastMessage.postValue("Failed – Device Owner required")
            }
        }
    }

    fun toggleDnsFilter(enable: Boolean) {
        viewModelScope.launch {
            if (enable) {
                val result = contentFilteringService.activateFullProtection()
                val success = result is com.example.takwafortress.services.filtering.ContentFilterResult.Success
                if (success) {
                    _settings.postValue(_settings.value?.copy(isDnsFilterActive = true) ?: return@launch)
                    _toastMessage.postValue("Content filtering enabled")
                } else {
                    _toastMessage.postValue("Failed to enable content filtering")
                }
            } else {
                _settings.postValue(_settings.value?.copy(isDnsFilterActive = false) ?: return@launch)
                _toastMessage.postValue("⚠️ Content filtering disabled")
            }
        }
    }

    // ═══════════════════════════════════════════
    // Private helpers
    // These are called from IO dispatcher — safe to call blocking APIs
    // ═══════════════════════════════════════════

    private fun isAppInstalled(packageName: String): Boolean {
        return try { packageManager.getPackageInfo(packageName, 0); true }
        catch (e: PackageManager.NameNotFoundException) { false }
    }

    private fun isAppSuspendedOrHidden(packageName: String): Boolean {
        if (!deviceOwnerService.isDeviceOwner()) return false
        val hidden = try { devicePolicyManager.isApplicationHidden(adminComponent, packageName) } catch (e: Exception) { false }
        if (hidden) return true
        return try {
            val method = PackageManager::class.java.getMethod("isApplicationSuspended", String::class.java)
            method.invoke(packageManager, packageName) as Boolean
        } catch (e: Exception) { false }
    }

    private fun blockApp(packageName: String, category: AppCategory): Boolean {
        if (!deviceOwnerService.isDeviceOwner()) return false
        return try {
            if (category == AppCategory.NUCLEAR) devicePolicyManager.setApplicationHidden(adminComponent, packageName, true)
            else devicePolicyManager.setPackagesSuspended(adminComponent, arrayOf(packageName), true)
            true
        } catch (e: Exception) { false }
    }

    private fun unblockApp(packageName: String, category: AppCategory): Boolean {
        if (!deviceOwnerService.isDeviceOwner()) return false
        return try {
            if (category == AppCategory.NUCLEAR) devicePolicyManager.setApplicationHidden(adminComponent, packageName, false)
            else devicePolicyManager.setPackagesSuspended(adminComponent, arrayOf(packageName), false)
            true
        } catch (e: Exception) { false }
    }

    private fun hasUserRestriction(restriction: String): Boolean {
        return try {
            val method = DevicePolicyManager::class.java.getMethod("getUserRestrictions", android.content.ComponentName::class.java)
            val bundle = method.invoke(devicePolicyManager, adminComponent) as android.os.Bundle
            bundle.getBoolean(restriction, false)
        } catch (e: Exception) { false }
    }

    private fun addRestriction(restriction: String): Boolean {
        if (!deviceOwnerService.isDeviceOwner()) return false
        return try {
            val method = DevicePolicyManager::class.java.getMethod("addUserRestriction", android.content.ComponentName::class.java, String::class.java)
            method.invoke(devicePolicyManager, adminComponent, restriction); true
        } catch (e: Exception) { false }
    }

    private fun removeRestriction(restriction: String): Boolean {
        if (!deviceOwnerService.isDeviceOwner()) return false
        return try {
            val method = DevicePolicyManager::class.java.getMethod("removeUserRestriction", android.content.ComponentName::class.java, String::class.java)
            method.invoke(devicePolicyManager, adminComponent, restriction); true
        } catch (e: Exception) { false }
    }

    // ═══════════════════════════════════════════
    // Protection report
    // ✅ FIX: was using runBlocking { repository.getAll() } on main thread.
    // Now a suspend function — called from within existing coroutines only.
    // repository.getAll() runs on IO dispatcher to avoid blocking.
    // ═══════════════════════════════════════════

    private suspend fun rebuildProtectionReport(list: List<AppItem>) {
        val totalBlocked = withContext(Dispatchers.IO) {
            try { repository.getAll().size } catch (e: Exception) { 0 }
        }
        val browsers = list.filter { it.category == AppCategory.BROWSER }
        val nuclear  = list.filter { it.category == AppCategory.NUCLEAR }
        _protectionReport.postValue(
            ProtectionReport(
                isDnsActive       = _settings.value?.isDnsFilterActive ?: true,
                suspendedBrowsers = browsers.count { it.isBlocked },
                totalBrowsers     = browsers.size,
                hiddenNuclearApps = nuclear.count { it.isBlocked },
                totalNuclearApps  = nuclear.size,
                totalBlockedApps  = totalBlocked
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}