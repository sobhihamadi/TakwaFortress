package com.example.takwafortress.model.builders

import com.example.takwafortress.model.entities.BlockedApp
import com.example.takwafortress.model.entities.IdentifierBlockedApp
import com.example.takwafortress.model.interfaces.ID

class BlockedAppBuilder private constructor() {

    private var packageName: String? = null
    private var appName: String? = null
    private var isSystemApp: Boolean? = null
    private var isSuspended: Boolean? = null
    private var blockReason: String? = null
    private var detectedDate: Long? = null
    private var isInstalled: Boolean = true  // ✅ NEW: Default to true for backward compatibility
    private var isPreBlocked: Boolean = false  // ✅ NEW: Default to false

    fun setPackageName(packageName: String): BlockedAppBuilder {
        this.packageName = packageName
        return this
    }

    fun setAppName(appName: String): BlockedAppBuilder {
        this.appName = appName
        return this
    }

    fun setIsSystemApp(isSystemApp: Boolean): BlockedAppBuilder {
        this.isSystemApp = isSystemApp
        return this
    }

    fun setIsSuspended(isSuspended: Boolean): BlockedAppBuilder {
        this.isSuspended = isSuspended
        return this
    }

    fun setBlockReason(blockReason: String): BlockedAppBuilder {
        this.blockReason = blockReason
        return this
    }

    fun setDetectedDate(detectedDate: Long): BlockedAppBuilder {
        this.detectedDate = detectedDate
        return this
    }

    // ✅ NEW: Set if app is currently installed
    fun setIsInstalled(isInstalled: Boolean): BlockedAppBuilder {
        this.isInstalled = isInstalled
        return this
    }

    // ✅ NEW: Set if app was pre-blocked (added before installation)
    fun setIsPreBlocked(isPreBlocked: Boolean): BlockedAppBuilder {
        this.isPreBlocked = isPreBlocked
        return this
    }

    fun build(): BlockedApp {
        require(packageName != null) { "Package name is required" }
        require(appName != null) { "App name is required" }
        require(isSystemApp != null) { "System app status is required" }
        require(isSuspended != null) { "Suspended status is required" }
        require(blockReason != null) { "Block reason is required" }
        require(detectedDate != null) { "Detected date is required" }

        return BlockedApp(
            packageName = packageName!!,
            appName = appName!!,
            isSystemApp = isSystemApp!!,
            isSuspended = isSuspended!!,
            blockReason = blockReason!!,
            detectedDate = detectedDate!!,
            isInstalled = isInstalled,  // ✅ NEW
            isPreBlocked = isPreBlocked  // ✅ NEW
        )
    }

    companion object {
        fun newBuilder(): BlockedAppBuilder {
            return BlockedAppBuilder()
        }
    }
}

class IdentifierBlockedAppBuilder private constructor() {

    private var id: ID? = null
    private var blockedApp: BlockedApp? = null

    fun setId(id: ID): IdentifierBlockedAppBuilder {
        require(id.isNotEmpty()) { "ID cannot be empty" }
        this.id = id
        return this
    }

    fun setBlockedApp(blockedApp: BlockedApp): IdentifierBlockedAppBuilder {
        this.blockedApp = blockedApp
        return this
    }

    fun build(): IdentifierBlockedApp {
        require(id != null) { "ID is required to build IdentifierBlockedApp" }
        require(blockedApp != null) { "BlockedApp is required to build IdentifierBlockedApp" }

        val ba = blockedApp!!

        return IdentifierBlockedApp(
            id = id!!,
            packageName = ba.getPackageName(),
            appName = ba.getAppName(),
            isSystemApp = ba.getIsSystemApp(),
            isSuspended = ba.getIsSuspended(),
            blockReason = ba.getBlockReason(),
            detectedDate = ba.getDetectedDate(),
            isInstalled = ba.getIsInstalled(),  // ✅ NEW
            isPreBlocked = ba.getIsPreBlocked()  // ✅ NEW
        )
    }

    companion object {
        fun newBuilder(): IdentifierBlockedAppBuilder {
            return IdentifierBlockedAppBuilder()
        }
    }
}