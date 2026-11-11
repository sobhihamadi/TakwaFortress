package com.example.takwafortress.services.core

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.takwafortress.receivers.DeviceAdminReceiver

class DeviceOwnerService(private val context: Context) {

    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)
    private val packageManager: PackageManager = context.packageManager

    /**
     * Checks if this app is the Device Owner.
     */
    fun isDeviceOwner(): Boolean {
        return try {
            devicePolicyManager.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if this app is at least a Device Admin (legacy check).
     */
    fun isDeviceAdmin(): Boolean {
        return try {
            devicePolicyManager.isAdminActive(adminComponent)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the Device Owner package name (should be this app).
     */
    fun getDeviceOwnerPackage(): String? {
        return if (isDeviceOwner()) {
            context.packageName
        } else {
            null
        }
    }

    /**
     * Verifies Device Owner status and returns detailed info.
     */
    fun getDeviceOwnerStatus(): DeviceOwnerStatus {
        return when {
            isDeviceOwner() -> DeviceOwnerStatus.Active(context.packageName)
            isDeviceAdmin() -> DeviceOwnerStatus.AdminOnly
            else -> DeviceOwnerStatus.NotActivated
        }
    }

    /**
     * Blocks uninstallation of this app (Device Owner required).
     */
    fun blockUninstall(): Result<Unit> {
        return try {
            if (!isDeviceOwner()) {
                return Result.failure(Exception("Device Owner not active"))
            }

            devicePolicyManager.setUninstallBlocked(
                adminComponent,
                context.packageName,
                true
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Hides apps from the launcher and app drawer safely.
     * SAFETY UPDATE: Automatically filters out system apps and self-hiding to prevent boot-loops.
     */
    fun hideApps(packageNames: List<String>): Result<Unit> {
        return try {
            if (!isDeviceOwner()) {
                return Result.failure(Exception("Device Owner not active"))
            }

            packageNames.forEach { packageName ->
                // SAFETY CHECK: Never allow hiding our own app or any system packages
                if (packageName != context.packageName && !isSystemApp(packageName)) {
                    devicePolicyManager.setApplicationHidden(
                        adminComponent,
                        packageName,
                        true
                    )
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Suspends apps safely (greyed out but visible).
     * SAFETY UPDATE: Prevents suspending system or critical packages.
     */
    fun suspendApps(packageNames: List<String>): Result<Unit> {
        return try {
            if (!isDeviceOwner()) {
                return Result.failure(Exception("Device Owner not active"))
            }

            val safePackages = packageNames.filter { packageName ->
                packageName != context.packageName && !isSystemApp(packageName)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && safePackages.isNotEmpty()) {
                devicePolicyManager.setPackagesSuspended(
                    adminComponent,
                    safePackages.toTypedArray(),
                    true
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Helper to check if a package is a pre-installed system app (prevents boot loops).
     */
    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (e: Exception) {
            false // If package not found, default to false
        }
    }

    /**
     * Disables USB debugging (blocks ADB).
     */
    fun disableUsbDebugging(): Result<Unit> {
        return try {
            if (!isDeviceOwner()) {
                return Result.failure(Exception("Device Owner not active"))
            }

            devicePolicyManager.addUserRestriction(
                adminComponent,
                android.os.UserManager.DISALLOW_DEBUGGING_FEATURES
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Forces automatic time (prevents time travel to skip commitment period).
     */
    fun forceAutoTime(): Result<Unit> {
        return try {
            if (!isDeviceOwner()) {
                return Result.failure(Exception("Device Owner not active"))
            }

            devicePolicyManager.setAutoTimeRequired(adminComponent, true)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Disables factory reset from settings (only hardware buttons work).
     */
    fun disableFactoryReset(): Result<Unit> {
        return try {
            if (!isDeviceOwner()) {
                return Result.failure(Exception("Device Owner not active"))
            }

            devicePolicyManager.addUserRestriction(
                adminComponent,
                android.os.UserManager.DISALLOW_FACTORY_RESET
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks if a specific app is hidden.
     */
    fun isAppHidden(packageName: String): Boolean {
        return try {
            if (!isDeviceOwner()) return false
            devicePolicyManager.isApplicationHidden(adminComponent, packageName)
        } catch (e: Exception) {
            false
        }
    }
}

sealed class DeviceOwnerStatus {
    data class Active(val packageName: String) : DeviceOwnerStatus()
    object AdminOnly : DeviceOwnerStatus()
    object NotActivated : DeviceOwnerStatus()
}