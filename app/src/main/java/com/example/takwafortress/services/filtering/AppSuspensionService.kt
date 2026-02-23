package com.example.takwafortress.services.filtering

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.takwafortress.model.builders.BlockedAppBuilder
import com.example.takwafortress.model.builders.IdentifierBlockedAppBuilder
import com.example.takwafortress.model.entities.BlockedApp
import com.example.takwafortress.model.entities.IdentifierBlockedApp
import com.example.takwafortress.receivers.DeviceAdminReceiver
import com.example.takwafortress.repository.implementations.LocalBlockedAppRepository
import com.example.takwafortress.services.core.DeviceOwnerService
import com.example.takwafortress.util.constants.BlockedPackages
import java.util.UUID

class AppSuspensionService(private val context: Context) {

    companion object {
        /**
         * Chrome package name - the only allowed browser.
         */
        private const val CHROME_PACKAGE = "com.android.chrome"
    }

    private val deviceOwnerService = DeviceOwnerService(context)
    private val blockedAppRepository = LocalBlockedAppRepository(context)
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)
    private val packageManager = context.packageManager

    /**
     * Suspends browsers (grey them out) except Chrome.
     * Nuclear apps (Telegram, Reddit, X, Discord) are hidden instead.
     */
    suspend fun suspendBrowsers(): SuspensionResult {
        if (!deviceOwnerService.isDeviceOwner()) {
            return SuspensionResult.DeviceOwnerRequired
        }

        val installedBrowsers = detectInstalledBrowsers()
        val browsersToSuspend = installedBrowsers.filter {
            it != CHROME_PACKAGE &&
                    it !in BlockedPackages.NUCLEAR_BLACKLIST
        }

        return if (browsersToSuspend.isEmpty()) {
            SuspensionResult.NoBrowsersToSuspend
        } else {
            suspendApps(browsersToSuspend)
        }
    }

    /**
     * Hides nuclear blacklist apps (Telegram, Reddit, X, Discord).
     */
    suspend fun hideNuclearApps(): SuspensionResult {
        if (!deviceOwnerService.isDeviceOwner()) {
            return SuspensionResult.DeviceOwnerRequired
        }

        val installedNuclearApps = detectInstalledNuclearApps()

        return if (installedNuclearApps.isEmpty()) {
            SuspensionResult.NoAppsToHide
        } else {
            hideApps(installedNuclearApps)
        }
    }

    /**
     * Suspends a list of apps (greys them out).
     */
    suspend fun suspendApps(packageNames: List<String>): SuspensionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return SuspensionResult.NotSupported
        }

        return try {
            val suspended = mutableListOf<String>()
            val failed = mutableListOf<String>()

            packageNames.forEach { packageName ->
                try {
                    val packagesArray = arrayOf(packageName)
                    val result = devicePolicyManager.setPackagesSuspended(
                        adminComponent,
                        packagesArray,
                        true
                    )

                    if (result.isEmpty()) {
                        suspended.add(packageName)
                        saveBlockedApp(packageName, isSuspended = true, isHidden = false)
                    } else {
                        failed.add(packageName)
                    }
                } catch (e: Exception) {
                    failed.add(packageName)
                }
            }

            SuspensionResult.Success(
                suspended = suspended,
                failed = failed
            )
        } catch (e: Exception) {
            SuspensionResult.Failed(e.message ?: "Unknown error")
        }
    }

    /**
     * Hides apps completely (removes from launcher/drawer).
     */
    suspend fun hideApps(packageNames: List<String>): SuspensionResult {
        return try {
            val hidden = mutableListOf<String>()
            val failed = mutableListOf<String>()

            packageNames.forEach { packageName ->
                val result = deviceOwnerService.hideApps(listOf(packageName))

                result.fold(
                    onSuccess = {
                        hidden.add(packageName)
                        saveBlockedApp(packageName, isSuspended = false, isHidden = true)
                    },
                    onFailure = {
                        failed.add(packageName)
                    }
                )
            }

            SuspensionResult.Success(
                suspended = hidden,
                failed = failed
            )
        } catch (e: Exception) {
            SuspensionResult.Failed(e.message ?: "Unknown error")
        }
    }

    /**
     * Saves blocked app to repository.
     */
    private suspend fun saveBlockedApp(packageName: String, isSuspended: Boolean, isHidden: Boolean) {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val blockedApp = BlockedAppBuilder.newBuilder()
                .setPackageName(packageName)
                .setAppName(appName)
                .setIsSystemApp(isSystemApp)
                .setIsSuspended(isSuspended)
                .setBlockReason(if (isHidden) "Nuclear blacklist app" else "Browser app")
                .setDetectedDate(System.currentTimeMillis())
                .build()

            val identifierBlockedApp = IdentifierBlockedAppBuilder.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setBlockedApp(blockedApp)
                .build()

            blockedAppRepository.blockApp(identifierBlockedApp)
        } catch (e: Exception) {
            // App might have been uninstalled already
        }
    }

    /**
     * Detects all installed browsers on the device.
     */
    fun detectInstalledBrowsers(): List<String> {
        val browsers = mutableListOf<String>()

        BlockedPackages.BROWSERS.keys.forEach { packageName ->
            if (isAppInstalled(packageName)) {
                browsers.add(packageName)
            }
        }

        return browsers
    }

    /**
     * Detects installed nuclear blacklist apps.
     */
    fun detectInstalledNuclearApps(): List<String> {
        val nuclearApps = mutableListOf<String>()

        BlockedPackages.NUCLEAR_BLACKLIST.keys.forEach { packageName ->
            if (isAppInstalled(packageName)) {
                nuclearApps.add(packageName)
            }
        }

        return nuclearApps
    }

    /**
     * Checks if an app is installed.
     */
    fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Checks if an app is currently suspended.
     */
    fun isAppSuspended(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                (applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if an app is hidden.
     */
    fun isAppHidden(packageName: String): Boolean {
        return deviceOwnerService.isAppHidden(packageName)
    }

    /**
     * Gets suspension status report.
     */
    suspend fun getSuspensionReport(): SuspensionReport {
        val allBrowsers = BlockedPackages.BROWSERS.keys
        val allNuclearApps = BlockedPackages.NUCLEAR_BLACKLIST.keys

        val installedBrowsers = allBrowsers.filter { isAppInstalled(it) }
        val installedNuclearApps = allNuclearApps.filter { isAppInstalled(it) }

        val suspendedBrowsers = installedBrowsers.filter { isAppSuspended(it) }
        val hiddenNuclearApps = installedNuclearApps.filter { isAppHidden(it) }

        return SuspensionReport(
            totalBrowsers = installedBrowsers.size,
            suspendedBrowsers = suspendedBrowsers.size,
            totalNuclearApps = installedNuclearApps.size,
            hiddenNuclearApps = hiddenNuclearApps.size,
            browsersList = installedBrowsers.map {
                AppSuspensionStatus(
                    packageName = it,
                    appName = getAppName(it),
                    isSuspended = isAppSuspended(it),
                    isHidden = false
                )
            },
            nuclearAppsList = installedNuclearApps.map {
                AppSuspensionStatus(
                    packageName = it,
                    appName = getAppName(it),
                    isSuspended = false,
                    isHidden = isAppHidden(it)
                )
            }
        )
    }

    /**
     * Gets app name from package name.
     */
    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    /**
     * Gets user-friendly explanation of app suspension.
     */
    fun getSuspensionExplanation(): String {
        return """
            📱 APP SUSPENSION STRATEGY
            
            BROWSERS (Greyed Out):
            - Opera, Firefox, Brave, Edge, etc.
            - Icons are VISIBLE but GREY
            - Clicking shows: "App suspended by Taqwa"
            - You can uninstall them to free space
            
            Why grey instead of hide?
            ✅ You stay in control of your storage
            ✅ Transparency - you know what's blocked
            ✅ Psychological reminder of your commitment
            
            NUCLEAR APPS (Completely Hidden):
            - Telegram, Reddit, X (Twitter), Discord
            - Icons are INVISIBLE
            - Cannot be accessed at all
            
            Why hidden?
            ❌ These apps have secret NSFW channels
            ❌ DNS cannot filter content inside them
            ❌ Only solution: complete removal
            
            ALLOWED BROWSER:
            ✅ Chrome (Managed)
            - Incognito disabled
            - SafeSearch forced
            - All traffic filtered via CleanBrowsing DNS
        """.trimIndent()
    }

    /**
     * Unsuspends an app (for testing or emergencies).
     */
    suspend fun unsuspendApp(packageName: String): SuspensionResult {
        if (!deviceOwnerService.isDeviceOwner()) {
            return SuspensionResult.DeviceOwnerRequired
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val packagesArray = arrayOf(packageName)
                devicePolicyManager.setPackagesSuspended(
                    adminComponent,
                    packagesArray,
                    false // false = unsuspend
                )

                blockedAppRepository.unblockApp(packageName)

                SuspensionResult.Success(
                    suspended = emptyList(),
                    failed = emptyList()
                )
            } else {
                SuspensionResult.NotSupported
            }
        } catch (e: Exception) {
            SuspensionResult.Failed(e.message ?: "Unknown error")
        }
    }

    /**
     * Unhides an app (for testing or emergencies).
     */
//    suspend fun unhideApp(packageName: String): SuspensionResult {
//        if (!deviceOwnerService.isDeviceOwner()) {
//            return SuspensionResult.DeviceOwnerRequired
//        }
//
//        return try {
//            val result = deviceOwnerService.unhideApp(packageName)
//
//            result.fold(
//                onSuccess = {
//                    blockedAppRepository.unblockApp(packageName)
//                    SuspensionResult.Success(
//                        suspended = emptyList(),
//                        failed = emptyList()
//                    )
//                },
//                onFailure = {
//                    SuspensionResult.Failed(it.message ?: "Unknown error")
//                }
//            )
//        } catch (e: Exception) {
//            SuspensionResult.Failed(e.message ?: "Unknown error")
//        }
//    }
}

sealed class SuspensionResult {
    data class Success(
        val suspended: List<String>,
        val failed: List<String>
    ) : SuspensionResult()
    object NoBrowsersToSuspend : SuspensionResult()
    object NoAppsToHide : SuspensionResult()
    object DeviceOwnerRequired : SuspensionResult()
    object NotSupported : SuspensionResult()
    data class Failed(val reason: String) : SuspensionResult()
}

data class SuspensionReport(
    val totalBrowsers: Int,
    val suspendedBrowsers: Int,
    val totalNuclearApps: Int,
    val hiddenNuclearApps: Int,
    val browsersList: List<AppSuspensionStatus>,
    val nuclearAppsList: List<AppSuspensionStatus>
)

data class AppSuspensionStatus(
    val packageName: String,
    val appName: String,
    val isSuspended: Boolean,
    val isHidden: Boolean
)