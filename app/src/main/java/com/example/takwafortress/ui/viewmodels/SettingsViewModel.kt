package com.example.takwafortress.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.takwafortress.services.core.DeviceOwnerService
import com.example.takwafortress.services.core.LicenseValidationService
import com.example.takwafortress.services.filtering.ContentFilteringService
import com.example.takwafortress.services.filtering.ContentFilterResult
import com.example.takwafortress.services.filtering.DnsTestResult
import com.example.takwafortress.services.monitoring.AppInstallMonitorService
import com.example.takwafortress.services.security.SafeModeBlockerService
import com.example.takwafortress.services.security.FactoryResetProtectionService
import com.example.takwafortress.services.monitoring.TimeProtectionService
import com.example.takwafortress.util.crypto.HardwareIdGenerator
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val licenseValidationService = LicenseValidationService(application)
    private val deviceOwnerService = DeviceOwnerService(application)
    private val contentFilteringService = ContentFilteringService(application)
    private val safeModeBlockerService = SafeModeBlockerService(application)
    private val frpService = FactoryResetProtectionService(application)
    private val timeProtectionService = TimeProtectionService(application)
    private val appInstallMonitorService = AppInstallMonitorService(application)

    // UI State
    private val _userInfo = MutableLiveData<UserInfo>()
    val userInfo: LiveData<UserInfo> = _userInfo

    private val _systemStatus = MutableLiveData<SystemStatus>()
    val systemStatus: LiveData<SystemStatus> = _systemStatus

    init {
        loadUserInfo()
        loadSystemStatus()
    }

    /**
     * Loads user information.
     */
    private fun loadUserInfo() {
        viewModelScope.launch {
            val user = licenseValidationService.getCurrentUser()
            if (user != null) {
                _userInfo.postValue(
                    UserInfo(
                        email = user.email,
                        licenseKey = user.selectedPlan,
                        hardwareId = user.deviceId,
                        licenseStatus = user.subscriptionStatus.name,
                        purchaseDate = user.commitmentStartDate,
                        totalPurchaseCount = user.commitmentDays
                    )
                )
            }
        }
    }

    /**
     * Loads system protection status.
     */
    private fun loadSystemStatus() {
        viewModelScope.launch {
            val isDeviceOwner = deviceOwnerService.isDeviceOwner()

            // Get comprehensive protection status from ContentFilteringService
            val protectionStatus = contentFilteringService.getProtectionStatus()

            val safeModeStatus = safeModeBlockerService.getProtectionStatus()
            val frpStatus = frpService.getFrpStatus()
            val timeStatus = timeProtectionService.getTimeProtectionStatus()
            val monitoringActive = appInstallMonitorService.isMonitoring()

            _systemStatus.postValue(
                SystemStatus(
                    isDeviceOwnerActive = isDeviceOwner,
                    isDnsFilterActive = protectionStatus.dnsFilterActive,
                    isChromeManaged = protectionStatus.chromeManagedActive,
                    areBrowsersBlocked = protectionStatus.browsersBlocked > 0, // Convert Int to Boolean
                    safeModeProtection = safeModeStatus.toString(),
                    isFactoryResetBlocked = frpStatus is com.example.takwafortress.services.security.FrpStatus.Protected,
                    isTimeProtected = timeStatus is com.example.takwafortress.services.monitoring.TimeProtectionStatus.FullyProtected,
                    isMonitoringActive = monitoringActive
                )
            )
        }
    }

    /**
     * Gets device information using static HardwareIdGenerator calls.
     */
    fun getDeviceInfo(): LiveData<DeviceInfoDisplay> {
        val result = MutableLiveData<DeviceInfoDisplay>()
        viewModelScope.launch {
            val context = getApplication<Application>()
            val hwid = HardwareIdGenerator.extractHardwareId(context)

            result.postValue(
                DeviceInfoDisplay(
                    manufacturer = android.os.Build.MANUFACTURER,
                    model = android.os.Build.MODEL,
                    androidVersion = android.os.Build.VERSION.RELEASE,
                    hardwareId = hwid
                )
            )
        }
        return result
    }

    /**
     * Tests DNS filter using ContentFilteringService.
     */
    fun testDnsFilter(): LiveData<String> {
        val result = MutableLiveData<String>()
        viewModelScope.launch {
            val testResult = contentFilteringService.testDnsFilter()
            result.postValue(
                when (testResult) {
                    is DnsTestResult.Success ->
                        "✅ ${testResult.message}"
                    is DnsTestResult.Failed ->
                        "⚠️ ${testResult.message}"
                    is DnsTestResult.Error ->
                        "❌ ${testResult.error}"
                }
            )
        }
        return result
    }

    /**
     * Activates full content filtering protection.
     */
    fun activateContentFiltering(): LiveData<String> {
        val result = MutableLiveData<String>()
        viewModelScope.launch {
            val activationResult = contentFilteringService.activateFullProtection()
            result.postValue(
                when (activationResult) {
                    is ContentFilterResult.Success ->
                        "✅ Protection activated\n${activationResult.details}"
                    is ContentFilterResult.DeviceOwnerRequired ->
                        "❌ Device Owner required"
                    is ContentFilterResult.Failed ->
                        "❌ Activation failed: ${activationResult.reason}"
                }
            )
        }
        return result
    }

    /**
     * Gets content filtering status report.
     */
    fun getContentFilteringReport(): LiveData<String> {
        val result = MutableLiveData<String>()
        viewModelScope.launch {
            val report = contentFilteringService.getStatusReport()
            result.postValue(report)
        }
        return result
    }

    /**
     * Verifies Chrome configuration.
     */
    fun verifyChromeConfig(): LiveData<String> {
        val result = MutableLiveData<String>()
        viewModelScope.launch {
            val protectionStatus = contentFilteringService.getProtectionStatus()

            val statusMessage = buildString {
                appendLine("🌐 CHROME CONFIGURATION")
                appendLine()

                if (protectionStatus.chromeManagedActive) {
                    appendLine("✅ Chrome is managed")
                    appendLine()
                    appendLine("Active Policies:")
                    appendLine("  • SafeSearch: Forced")
                    appendLine("  • Incognito Mode: Disabled")
                    appendLine("  • DNS-over-HTTPS: Disabled")
                    appendLine("  • Default Browser: Set to Chrome")
                } else {
                    appendLine("❌ Chrome is not managed")
                    appendLine()
                    appendLine("Please activate content filtering to manage Chrome.")
                }
            }

            result.postValue(statusMessage)
        }
        return result
    }

    /**
     * Gets protection score (0-100).
     * Updated to use ContentFilteringService layers.
     */
    fun getProtectionScore(): LiveData<Int> {
        val result = MutableLiveData<Int>()
        viewModelScope.launch {
            val status = _systemStatus.value ?: return@launch
            var score = 0

            // Core requirements (40 points)
            if (status.isDeviceOwnerActive) score += 20
            if (status.isDnsFilterActive) score += 20

            // Content filtering layers (30 points)
            if (status.isChromeManaged) score += 15
            if (status.areBrowsersBlocked) score += 15

            // Additional protections (30 points)
            if (status.isFactoryResetBlocked) score += 10
            if (status.isTimeProtected) score += 10
            if (status.isMonitoringActive) score += 10

            result.postValue(score)
        }
        return result
    }

    /**
     * Exports diagnostic report.
     * Updated to include ContentFilteringService status.
     */
    fun exportDiagnosticReport(): LiveData<String> {
        val result = MutableLiveData<String>()
        viewModelScope.launch {
            val user = _userInfo.value
            val system = _systemStatus.value
            val context = getApplication<Application>()

            val hwid = HardwareIdGenerator.extractHardwareId(context)
            val deviceName = HardwareIdGenerator.getDeviceName()

            val contentFilteringStatus = contentFilteringService.getStatusReport()

            val report = """
                TAQWA FORTRESS - DIAGNOSTIC REPORT
                =====================================
                
                USER INFO:
                - Email: ${user?.email ?: "N/A"}
                - License: ${user?.licenseKey ?: "N/A"}
                - Status: ${user?.licenseStatus ?: "N/A"}
                - Purchase Date: ${user?.purchaseDate ?: "N/A"}
                
                DEVICE INFO:
                - Device: $deviceName
                - Android: ${android.os.Build.VERSION.RELEASE}
                - Hardware ID: $hwid
                
                CORE PROTECTION:
                - Device Owner: ${if (system?.isDeviceOwnerActive == true) "✅" else "❌"}
                
                CONTENT FILTERING (3 LAYERS):
                - DNS Filter: ${if (system?.isDnsFilterActive == true) "✅" else "❌"}
                - Chrome Managed: ${if (system?.isChromeManaged == true) "✅" else "❌"}
                - Browsers Blocked: ${if (system?.areBrowsersBlocked == true) "✅" else "❌"}
                
                ADDITIONAL PROTECTIONS:
                - Safe Mode: ${system?.safeModeProtection ?: "Unknown"}
                - Factory Reset: ${if (system?.isFactoryResetBlocked == true) "✅" else "❌"}
                - Time Protection: ${if (system?.isTimeProtected == true) "✅" else "❌"}
                - App Monitoring: ${if (system?.isMonitoringActive == true) "✅" else "❌"}
                
                DETAILED CONTENT FILTERING STATUS:
                $contentFilteringStatus
                
                Report generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}
            """.trimIndent()

            result.postValue(report)
        }
        return result
    }

    /**
     * Refreshes all settings data.
     */
    fun refresh() {
        loadUserInfo()
        loadSystemStatus()
    }
}

data class UserInfo(
    val email: String,
    val licenseKey: String,
    val hardwareId: String,
    val licenseStatus: String,
    val purchaseDate: Timestamp?,
    val totalPurchaseCount: Int
)

data class SystemStatus(
    val isDeviceOwnerActive: Boolean,
    val isDnsFilterActive: Boolean,
    val isChromeManaged: Boolean,
    val areBrowsersBlocked: Boolean,
    val safeModeProtection: String,
    val isFactoryResetBlocked: Boolean,
    val isTimeProtected: Boolean,
    val isMonitoringActive: Boolean
)

data class DeviceInfoDisplay(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val hardwareId: String
)