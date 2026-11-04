package com.example.takwafortress.services.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Log
import com.example.takwafortress.model.entities.BlockedApp
import com.example.takwafortress.receivers.DeviceAdminReceiver
import com.example.takwafortress.util.constants.BlockedPackages

/**
 * Safety rules shared by every place that hides or suspends a package.
 *
 * WHY THIS EXISTS
 * The old "is this a browser?" test asked whether a package can open https://www.google.com.
 * Dozens of NON-browser apps can (Google app, Maps, ARCore, Samsung system apps…), so system
 * components were hidden at every boot. That broke the camera and the hotspot, and can stop
 * the phone from booting. Rules now:
 *   1. A browser probe uses a made-up host, so only apps that open ANY web link match.
 *   2. System / boot-critical packages are never hidden or suspended, unless they are a
 *      known browser or the user's own explicit block list names them.
 *   3. If the phone reboots 3 times in 10 minutes, protection pauses and everything the app
 *      hid is restored (boot-loop breaker).
 */
object SystemSafetyGuard {

    private const val TAG = "SystemSafetyGuard"
    private const val PREFS = "system_safety_guard"
    private const val KEY_BOOTS = "boot_times"
    private const val KEY_PAUSED_UNTIL = "paused_until"

    private const val LOOP_WINDOW_MS = 10L * 60 * 1000
    private const val LOOP_BOOTS = 3
    private const val PAUSE_MS = 2L * 60 * 60 * 1000

    /** Only apps that open ANY https link match this (browsers), not host-specific apps. */
    const val BROWSER_PROBE_URI = "https://takwa-browser-probe.invalid/"

    /** System browsers the user does want hidden. */
    private val HIDABLE_SYSTEM_BROWSERS = setOf(
        "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
        "com.sec.android.app.samsunginternet", "com.android.browser",
        "com.mi.globalbrowser", "com.mi.android.globalbrowser", "com.huawei.browser",
        "com.heytap.browser", "com.oppo.browser", "com.coloros.browser", "com.vivo.browser",
        "com.tcl.browser", "com.transsion.phoenix", "com.opera.browser",
        "org.mozilla.firefox", "com.brave.browser", "com.microsoft.emmx"
    )

    /** Never hidden/suspended (even if non-system), unless explicitly listed above/by the user. */
    private val NEVER_HIDE_PREFIXES = listOf(
        "android", "com.android.", "com.google.android.", "com.google.ar.",
        "com.samsung.", "com.sec.", "com.qualcomm.", "com.qti.", "com.mediatek",
        "org.codeaurora."
    )

    // ── Protection check ──────────────────────────────────────────────────────

    /** True when [pkg] must NOT be hidden or suspended by the fortress. */
    fun isProtected(context: Context, pkg: String): Boolean =
        isProtectedInternal(context, pkg) { runtimeCriticalPackages(context) }

    private fun isProtectedInternal(context: Context, pkg: String, critical: () -> Set<String>): Boolean {
        if (pkg == context.packageName) return true
        if (isExplicitlyHidable(pkg)) return false
        if (NEVER_HIDE_PREFIXES.any { pkg == it || pkg.startsWith(if (it.endsWith(".")) it else "$it.") }) return true
        if (isSystemPackage(context, pkg)) return true
        return pkg in critical()
    }

    /** Default launcher / keyboard / SMS / dialer. */
    fun isDefaultCriticalApp(context: Context, pkg: String): Boolean =
        pkg in runtimeCriticalPackages(context)

    private fun isExplicitlyHidable(pkg: String): Boolean =
        pkg in HIDABLE_SYSTEM_BROWSERS ||
                BlockedApp.isNuclearApp(pkg) ||
                pkg in BlockedPackages.ALL_BLOCKED

    private fun isSystemPackage(context: Context, pkg: String): Boolean = try {
        val flags = context.packageManager.getApplicationInfo(pkg, 0).flags
        flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    } catch (e: Exception) { false }

    /** Default launcher, keyboard, SMS app and dialer: hiding these breaks the phone. */
    private fun runtimeCriticalPackages(context: Context): Set<String> {
        val result = mutableSetOf<String>()
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.let { result.add(it) }
        } catch (_: Exception) {}
        try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let { result.add(it) }
        } catch (_: Exception) {}
        try { Telephony.Sms.getDefaultSmsPackage(context)?.let { result.add(it) } } catch (_: Exception) {}
        try {
            (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)
                ?.defaultDialerPackage?.let { result.add(it) }
        } catch (_: Exception) {}
        return result
    }

    // ── Boot-loop breaker ─────────────────────────────────────────────────────

    fun isPaused(context: Context): Boolean =
        System.currentTimeMillis() < prefs(context).getLong(KEY_PAUSED_UNTIL, 0L)

    /**
     * Call once per BOOT_COMPLETED. Returns true when 3 boots happened within 10 minutes:
     * protection is then paused for 2 hours (caller should restore hidden apps).
     */
    fun recordBoot(context: Context): Boolean {
        val now = System.currentTimeMillis()
        val p = prefs(context)
        val boots = (p.getString(KEY_BOOTS, "") ?: "")
            .split(",").mapNotNull { it.toLongOrNull() }
            .filter { now - it < LOOP_WINDOW_MS } + now
        return if (boots.size >= LOOP_BOOTS) {
            p.edit().putString(KEY_BOOTS, "").putLong(KEY_PAUSED_UNTIL, now + PAUSE_MS).apply()
            Log.w(TAG, "⚠️ ${boots.size} boots within 10 min — protection paused for 2h")
            true
        } else {
            p.edit().putString(KEY_BOOTS, boots.joinToString(",")).apply()
            false
        }
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    /** Unhides/unsuspends protected system packages (repairs damage from older versions). */
    fun restoreProtectedPackages(context: Context): Int = restore(context, onlyProtected = true)

    /** Emergency: unhides/unsuspends EVERYTHING the Device Owner hid or suspended. */
    fun restoreEverything(context: Context): Int = restore(context, onlyProtected = false)

    @Suppress("DEPRECATION")
    private fun restore(context: Context, onlyProtected: Boolean): Int {
        var fixed = 0
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (!dpm.isDeviceOwnerApp(context.packageName)) return 0
            val admin = ComponentName(context, DeviceAdminReceiver::class.java)
            val pm = context.packageManager
            val critical = runtimeCriticalPackages(context)   // computed once, not per package

            // MATCH_UNINSTALLED_PACKAGES also lists packages that are currently hidden
            val all = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong())
                )
            } else {
                pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            }

            for (info in all) {
                val pkg = info.packageName
                if (pkg == context.packageName) continue
                if (onlyProtected && !isProtectedInternal(context, pkg) { critical }) continue
                try {
                    if (dpm.isApplicationHidden(admin, pkg)) {
                        dpm.setApplicationHidden(admin, pkg, false); fixed++
                        Log.w(TAG, "Unhid $pkg")
                    }
                } catch (_: Exception) {}
                try {
                    if (dpm.isPackageSuspended(admin, pkg)) {
                        dpm.setPackagesSuspended(admin, arrayOf(pkg), false); fixed++
                        Log.w(TAG, "Unsuspended $pkg")
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "restore failed: ${e.message}")
        }
        return fixed
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
