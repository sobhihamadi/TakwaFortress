package com.example.takwafortress.receivers



import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.takwafortress.util.constants.AppConstants

/**
 * Device Admin Receiver - Required for Device Owner functionality.
 * This class handles Device Admin events and is the entry point for Device Owner.
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "${AppConstants.LOG_TAG}_DeviceAdmin"
    }

    /**
     * Called when Device Admin is enabled.
     */
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled")

        // Show notification
        showNotification(context, "Taqwa Device Admin Enabled", "Taqwa is now a Device Administrator")
    }

    /**
     * Called when Device Admin is disabled.
     * This should NEVER happen if Device Owner is active.
     */
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device Admin disabled - THIS SHOULD NOT HAPPEN!")

        // Alert user - something is wrong
        showNotification(context, "⚠️ Taqwa Disabled", "Device Admin was disabled unexpectedly")
    }

    /**
     * Called when user tries to disable Device Admin.
     * Return a message explaining why it cannot be disabled.
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.i(TAG, "Disable requested - showing warning")

        return """
            ⚠️ Cannot Disable Taqwa
            
            Taqwa is your Device Owner and cannot be disabled while:
            - Fortress period is active
            - Commitment has not expired
            
            To disable Taqwa:
            1. Wait for your commitment period to end
            2. Complete the full duration
            3. Then you can disable protection
            
            This is for your protection.
        """.trimIndent()
    }

    /**
     * Called when password is changed.
     */
    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        Log.i(TAG, "Device password changed")
    }

    /**
     * Called when password failed attempt.
     */
    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.i(TAG, "Password failed attempt")
    }

    /**
     * Called when password succeeded.
     */
    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.i(TAG, "Password succeeded")
    }

    /**
     * Called when lock task mode is entering.
     */
    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Log.i(TAG, "Lock task mode entering: $pkg")
    }

    /**
     * Called when lock task mode is exiting.
     */
    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Log.i(TAG, "Lock task mode exiting")
    }

    /**
     * Shows a notification to the user.
     */
    private fun showNotification(context: Context, title: String, message: String) {
        // TODO: Implement notification display
        Log.i(TAG, "Notification: $title - $message")
    }
}