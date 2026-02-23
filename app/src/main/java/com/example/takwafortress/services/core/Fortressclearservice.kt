package com.example.takwafortress.services.core

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.example.takwafortress.model.builders.IdentifierUserBuilder
import com.example.takwafortress.model.builders.UserBuilder
import com.example.takwafortress.model.enums.SubscriptionStatus
import com.example.takwafortress.receivers.DeviceAdminReceiver
import com.example.takwafortress.repository.implementations.FirebaseUserRepository
import com.example.takwafortress.repository.implementations.LocalBlockedAppRepository
import com.example.takwafortress.repository.implementations.LocalFortressPolicyRepository
import com.example.takwafortress.services.filtering.BlockedAppsManager
import com.example.takwafortress.services.filtering.ContentFilteringService
import com.example.takwafortress.util.constants.BlockedPackages
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FortressClearService(private val context: Context) {

    companion object {
        private const val TAG = "FortressClear"
    }

    private val devicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent       = ComponentName(context, DeviceAdminReceiver::class.java)
    private val deviceOwnerService   = DeviceOwnerService(context)
    private val blockedAppsManager   = BlockedAppsManager(context)
    private val localBlockedAppRepo  = LocalBlockedAppRepository(context)
    private val fortressPolicyRepo   = LocalFortressPolicyRepository(context)
    private val userRepository       = FirebaseUserRepository(context)
    private val auth                 = FirebaseAuth.getInstance()

    suspend fun clearEverything(): FortressClearResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "FORTRESS CLEAR STARTED")
        Log.i(TAG, "═══════════════════════════════════════")

        if (!deviceOwnerService.isDeviceOwner()) {
            Log.w(TAG, "Not Device Owner — clearing local data and Firestore only")
            clearLocalDataOnly()
            resetFirestoreAfterClear()
            return@withContext FortressClearResult.Success
        }

        val errors = mutableListOf<String>()

        // ── Step 1: Unsuspend browsers ────────────────────────────────
        Log.i(TAG, "Step 1: Unsuspending browsers...")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                devicePolicyManager.setPackagesSuspended(
                    adminComponent,
                    BlockedPackages.BROWSERS.keys.toTypedArray(),
                    false
                )
            }
            Log.i(TAG, "✅ Browsers unsuspended")
        } catch (e: Exception) {
            errors.add("Unsuspend browsers: ${e.message}").also { Log.e(TAG, "❌ $it") }
        }

        // ── Step 2: Unhide nuclear apps ───────────────────────────────
        Log.i(TAG, "Step 2: Unhiding nuclear apps...")
        try {
            BlockedPackages.NUCLEAR_BLACKLIST.keys.forEach { pkg ->
                try { devicePolicyManager.setApplicationHidden(adminComponent, pkg, false) }
                catch (e: Exception) { Log.w(TAG, "Could not unhide $pkg") }
            }
            Log.i(TAG, "✅ Nuclear apps unhidden")
        } catch (e: Exception) {
            errors.add("Unhide nuclear apps: ${e.message}").also { Log.e(TAG, "❌ $it") }
        }

        // ── Step 3: Unblock apps from LocalBlockedAppRepository ───────
        // AppSuspensionService saves here — this is the primary source of blocked apps
        Log.i(TAG, "Step 3: Clearing LocalBlockedAppRepository apps...")
        try {
            val repoApps = localBlockedAppRepo.getAll()
            Log.i(TAG, "Found ${repoApps.size} apps in LocalBlockedAppRepository")
            repoApps.forEach { app ->
                val pkg = app.getPackageName()
                try {
                    devicePolicyManager.setApplicationHidden(adminComponent, pkg, false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        devicePolicyManager.setPackagesSuspended(adminComponent, arrayOf(pkg), false)
                    }
                    Log.i(TAG, "✅ Unblocked: $pkg")
                } catch (e: Exception) { Log.w(TAG, "Could not unblock $pkg: ${e.message}") }
            }
        } catch (e: Exception) {
            errors.add("Clear repo apps: ${e.message}").also { Log.e(TAG, "❌ $it") }
        }

        // ── Step 4: Unblock user-added apps from BlockedAppsManager ───
        Log.i(TAG, "Step 4: Clearing BlockedAppsManager apps...")
        try {
            val userPkgs = blockedAppsManager.getBlockedPackages()
            Log.i(TAG, "Found ${userPkgs.size} apps in BlockedAppsManager")
            userPkgs.forEach { pkg ->
                try {
                    devicePolicyManager.setApplicationHidden(adminComponent, pkg, false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        devicePolicyManager.setPackagesSuspended(adminComponent, arrayOf(pkg), false)
                    }
                    blockedAppsManager.removeBlockedPackage(pkg)
                } catch (e: Exception) { Log.w(TAG, "Could not unblock $pkg: ${e.message}") }
            }
            Log.i(TAG, "✅ BlockedAppsManager apps cleared")
        } catch (e: Exception) {
            errors.add("Clear manager apps: ${e.message}").also { Log.e(TAG, "❌ $it") }
        }

        // ── Step 5: Remove user restrictions ──────────────────────────
        Log.i(TAG, "Step 5: Removing user restrictions...")
        listOf(
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES
        ).forEach { restriction ->
            try { devicePolicyManager.clearUserRestriction(adminComponent, restriction) }
            catch (e: Exception) { Log.w(TAG, "Could not remove $restriction") }
        }
        Log.i(TAG, "✅ User restrictions removed")

        // ── Step 6: Reset DNS ─────────────────────────────────────────
        Log.i(TAG, "Step 6: Resetting DNS...")
        try {
            devicePolicyManager.clearUserRestriction(
                adminComponent, UserManager.DISALLOW_CONFIG_PRIVATE_DNS
            )
        } catch (e: Exception) { Log.w(TAG, "DNS restriction removal: ${e.message}") }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                devicePolicyManager.setGlobalPrivateDnsModeOpportunistic(adminComponent)
            } else {
                android.provider.Settings.Global.putString(
                    context.contentResolver, "private_dns_mode", "opportunistic"
                )
            }
            Log.i(TAG, "✅ DNS reset to opportunistic")
        } catch (e: Exception) { Log.w(TAG, "DNS reset: ${e.message}") }

        // ── Step 7: Disable forced auto time ──────────────────────────
        Log.i(TAG, "Step 7: Disabling forced auto time...")
        try {
            devicePolicyManager.setAutoTimeRequired(adminComponent, false)
            Log.i(TAG, "✅ Auto time removed")
        } catch (e: Exception) { Log.w(TAG, "Auto time: ${e.message}") }

        // ── Step 8: Stop DNS VPN ──────────────────────────────────────
        Log.i(TAG, "Step 8: Stopping DNS VPN...")
        try {
            context.stopService(Intent(context, ContentFilteringService::class.java))
            Log.i(TAG, "✅ DNS VPN stopped")
        } catch (e: Exception) { Log.w(TAG, "Stop VPN: ${e.message}") }

        // ── Step 9: Remove Chrome policies ────────────────────────────
        Log.i(TAG, "Step 9: Removing Chrome policies...")
        try {
            devicePolicyManager.setApplicationRestrictions(
                adminComponent, "com.android.chrome", android.os.Bundle()
            )
            Log.i(TAG, "✅ Chrome policies removed")
        } catch (e: Exception) { Log.w(TAG, "Chrome policies: ${e.message}") }

        // ── Step 10: Allow app uninstall ──────────────────────────────
        Log.i(TAG, "Step 10: Allowing uninstall...")
        try {
            devicePolicyManager.setUninstallBlocked(adminComponent, context.packageName, false)
            Log.i(TAG, "✅ Uninstall unblocked")
        } catch (e: Exception) { Log.w(TAG, "Unblock uninstall: ${e.message}") }

        // ── Step 11: Clear local data ─────────────────────────────────
        Log.i(TAG, "Step 11: Clearing local data...")
        clearLocalDataOnly()

        // ── Step 12: Reset Firestore user document ────────────────────
        // ✅ Resets hasDeviceOwner, commitmentEndDate, commitmentStartDate,
        //    commitmentDays, subscriptionStatus so routing works correctly next login
        Log.i(TAG, "Step 12: Resetting Firestore user document...")
        resetFirestoreAfterClear()

        // ── Step 13: Remove Device Owner (MUST BE LAST) ───────────────
        Log.i(TAG, "Step 13: Removing Device Owner...")
        try {
            devicePolicyManager.clearDeviceOwnerApp(context.packageName)
            Log.i(TAG, "✅ Device Owner removed")
        } catch (e: Exception) {
            errors.add("Remove Device Owner: ${e.message}").also { Log.e(TAG, "❌ $it") }
        }

        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "FORTRESS CLEAR COMPLETE — Errors: ${errors.size}")
        Log.i(TAG, "═══════════════════════════════════════")

        if (errors.isEmpty()) FortressClearResult.Success
        else FortressClearResult.PartialSuccess(errors)
    }

    private suspend fun clearLocalDataOnly() {
        try {
            val all = localBlockedAppRepo.getAll()
            all.forEach { try { localBlockedAppRepo.delete(it.getId()) } catch (e: Exception) { } }
            Log.i(TAG, "✅ LocalBlockedAppRepository cleared (${all.size})")
        } catch (e: Exception) { Log.w(TAG, "Clear repo: ${e.message}") }

        try {
            fortressPolicyRepo.clearActivePolicy()
            Log.i(TAG, "✅ Fortress policy cleared")
        } catch (e: Exception) { Log.w(TAG, "Clear policy: ${e.message}") }

        try {
            context.getSharedPreferences("fortress_commitment", Context.MODE_PRIVATE)
                .edit().clear().apply()
            Log.i(TAG, "✅ Commitment prefs cleared")
        } catch (e: Exception) { Log.w(TAG, "Clear prefs: ${e.message}") }
    }

    /**
     * Resets the Firestore user document after a commitment/plan ends.
     *
     * Fields reset:
     *   hasDeviceOwner    → false   (user must re-setup Device Owner for next plan)
     *   subscriptionStatus → PENDING (user must select a new plan)
     *   commitmentEndDate  → null   (no active commitment)
     *   commitmentStartDate→ null
     *   commitmentDays     → 0
     *   selectedPlan       → ""
     *
     * Fields preserved:
     *   email, deviceId, createdAt   (permanent user identity fields)
     */
    private suspend fun resetFirestoreAfterClear() {
        try {
            val userId = auth.currentUser?.uid ?: run {
                Log.w(TAG, "No authenticated user — skipping Firestore reset")
                return
            }

            val existingUser = userRepository.get(userId)

            val resetUser = UserBuilder.newBuilder()
                .setEmail(existingUser.email)
                .setDeviceId(existingUser.deviceId)       // keep device ID
                .setSubscriptionStatus(SubscriptionStatus.PENDING)  // ✅ reset
                .setHasDeviceOwner(false)                 // ✅ reset
                .setSelectedPlan("")                      // ✅ reset
                .setCommitmentDays(0)                     // ✅ reset
                .setCommitmentStartDate(null)             // ✅ reset
                .setCommitmentEndDate(null)               // ✅ reset
                .setCreatedAt(existingUser.createdAt)     // keep
                .setUpdatedAt(Timestamp.now())
                .build()

            val updatedIdentifierUser = IdentifierUserBuilder
                .fromUser(userId, resetUser)
                .build()

            userRepository.setCurrentUser(updatedIdentifierUser)
            Log.i(TAG, "✅ Firestore user document reset successfully")
            Log.i(TAG, "   hasDeviceOwner = false")
            Log.i(TAG, "   subscriptionStatus = PENDING")
            Log.i(TAG, "   commitmentEndDate = null")
            Log.i(TAG, "   commitmentDays = 0")

        } catch (e: Exception) {
            Log.w(TAG, "Could not reset Firestore user document: ${e.message}")
        }
    }
}

sealed class FortressClearResult {
    object Success : FortressClearResult()
    data class PartialSuccess(val errors: List<String>) : FortressClearResult()
    object NotDeviceOwner : FortressClearResult()
}