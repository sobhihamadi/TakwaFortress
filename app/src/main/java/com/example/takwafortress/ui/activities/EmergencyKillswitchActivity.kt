package com.example.takwafortress.ui.activities

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.UserManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.takwafortress.R
import com.example.takwafortress.receivers.DeviceAdminReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * EMERGENCY KILLSWITCH ACTIVITY
 * This will remove ALL Device Owner restrictions and make the app uninstallable.
 */
class EmergencyKillswitchActivity : AppCompatActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private lateinit var statusText: TextView
    private lateinit var killswitchButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_killswitch)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, DeviceAdminReceiver::class.java)

        initViews()
        checkDeviceOwnerStatus()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        killswitchButton = findViewById(R.id.killswitchButton)

        killswitchButton.setOnClickListener {
            showConfirmationDialog()
        }
    }

    private fun checkDeviceOwnerStatus() {
        val isDeviceOwner = devicePolicyManager.isDeviceOwnerApp(packageName)

        if (isDeviceOwner) {
            statusText.text = """
                ⚠️ DEVICE OWNER ACTIVE ⚠️
                
                The app currently has Device Owner permissions.
                
                Tap the button below to:
                ✅ Remove Device Owner
                ✅ Remove ALL restrictions
                ✅ Re-enable factory reset
                ✅ Re-enable USB debugging
                ✅ Make app uninstallable
                
                This cannot be undone!
            """.trimIndent()
            killswitchButton.isEnabled = true
            killswitchButton.text = "🔴 REMOVE DEVICE OWNER"
        } else {
            statusText.text = """
                ✅ DEVICE OWNER NOT ACTIVE
                
                The app is not a Device Owner.
                You can now uninstall it normally.
            """.trimIndent()
            killswitchButton.isEnabled = false
            killswitchButton.text = "Already Disabled"
        }
    }

    private fun showConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ FINAL WARNING")
            .setMessage("""
                This will COMPLETELY remove Device Owner permissions.
                
                After this:
                ✅ You can uninstall the app
                ✅ Factory reset will work
                ✅ USB debugging will work
                ✅ All restrictions removed
                
                Are you ABSOLUTELY SURE?
            """.trimIndent())
            .setPositiveButton("YES, REMOVE DEVICE OWNER") { _, _ ->
                executeKillswitch()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executeKillswitch() {
        CoroutineScope(Dispatchers.Main).launch {
            statusText.text = "⏳ Removing Device Owner... Please wait..."
            killswitchButton.isEnabled = false

            val success = withContext(Dispatchers.IO) {
                try {
                    removeAllRestrictions()
                    true
                } catch (e: Exception) {
                    false
                }
            }

            if (success) {
                showSuccessDialog()
            } else {
                statusText.text = "❌ Failed to remove Device Owner. Try again."
                killswitchButton.isEnabled = true
            }
        }
    }

    /**
     * REMOVES ALL DEVICE OWNER RESTRICTIONS
     */
    private fun removeAllRestrictions(): Boolean {
        return try {
            // 1. Remove ALL user restrictions
            removeAllUserRestrictions()

            // 2. Unhide all apps
            unhideAllApps()

            // 3. Unsuspend all apps
            unsuspendAllApps()

            // 4. Remove uninstall block on Taqwa
            devicePolicyManager.setUninstallBlocked(adminComponent, packageName, false)

            // 5. Clear Device Owner (THIS IS THE KEY STEP)
            devicePolicyManager.clearDeviceOwnerApp(packageName)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Removes all user restrictions.
     */
    private fun removeAllUserRestrictions() {
        try {
            // Factory reset
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)

            // USB debugging
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)

            // DNS config
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
            }

            // Safe mode
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)

            // Remove auto-time requirement
            devicePolicyManager.setAutoTimeRequired(adminComponent, false)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Unhides all apps.
     */
    private fun unhideAllApps() {
        try {
            val blockedApps = listOf(
                "org.telegram.messenger",
                "com.reddit.frontpage",
                "com.twitter.android",
                "com.discord",
                "org.mozilla.firefox",
                "com.opera.browser",
                "com.brave.browser",
                "com.microsoft.emmx"
            )

            blockedApps.forEach { packageName ->
                try {
                    devicePolicyManager.setApplicationHidden(adminComponent, packageName, false)
                } catch (e: Exception) {
                    // App might not be installed
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Unsuspends all apps.
     */
    private fun unsuspendAllApps() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val blockedApps = listOf(
                    "org.mozilla.firefox",
                    "com.opera.browser",
                    "com.brave.browser",
                    "com.microsoft.emmx"
                )

                blockedApps.forEach { packageName ->
                    try {
                        devicePolicyManager.setPackagesSuspended(
                            adminComponent,
                            arrayOf(packageName),
                            false
                        )
                    } catch (e: Exception) {
                        // App might not be installed
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("✅ SUCCESS!")
            .setMessage("""
                Device Owner has been removed!
                
                ✅ All restrictions removed
                ✅ Factory reset enabled
                ✅ USB debugging enabled
                ✅ App can now be uninstalled
                
                You can now:
                - Uninstall the app normally
                - Use factory reset
                - Enable developer options
                
                Tap OK to close the app.
            """.trimIndent())
            .setPositiveButton("OK") { _, _ ->
                finishAffinity() // Close app completely
            }
            .setCancelable(false)
            .show()
    }
}