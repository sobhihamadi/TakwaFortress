package com.example.takwafortress.services.filtering

import com.example.takwafortress.services.security.SystemSafetyGuard
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.util.Log
import com.example.takwafortress.receivers.DeviceAdminReceiver
import com.example.takwafortress.repository.implementations.LocalBlockedAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Allowlist for apps.
 *
 * - At fortress activation every installed package is saved as the allowlist (snapshotInstalled).
 * - Any NEW package after that goes to a waiting list and is suspended (blocked).
 * - Waiting apps wait [WAIT_MS] (12h). The user can Approve (app is released once the 12h
 *   are over) or Reject (app goes to the blocked list immediately — done by the UI).
 * - The feature is only active when a snapshot exists (see isEnabled).
 */
class AppAllowlistManager(private val context: Context) {

    data class WaitingApp(
        val packageName: String,
        val appName: String,
        val installedAt: Long,
        val approved: Boolean
    ) {
        fun remainingMs(now: Long = System.currentTimeMillis()): Long =
            (installedAt + WAIT_MS - now).coerceAtLeast(0L)
    }

    companion object {
        const val WAIT_MS = 12L * 60 * 60 * 1000
        private const val TAG = "AppAllowlist"
        private const val PREFS = "app_allowlist_config"
        private const val KEY_ALLOWLIST = "allowlist"
        private const val KEY_WAITING = "waiting"
        private val LOCK = Any()
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dpm =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, DeviceAdminReceiver::class.java)

    // ── State ─────────────────────────────────────────────────────────────────

    /** Feature is on only after a snapshot was taken at activation. */
    fun isEnabled(): Boolean = prefs.contains(KEY_ALLOWLIST)

    fun isAllowed(pkg: String): Boolean = readAllowlist().contains(pkg)

    fun isWaiting(pkg: String): Boolean = getWaiting().any { it.packageName == pkg }

    fun getWaiting(): List<WaitingApp> = synchronized(LOCK) { readWaiting() }

    // ── Activation ────────────────────────────────────────────────────────────

    /** Called at fortress activation: everything installed right now is allowed. */
    fun snapshotInstalled() = synchronized(LOCK) {
        val pkgs = context.packageManager.getInstalledApplications(0)
            .map { it.packageName }.toSet()
        writeAllowlist(pkgs)
        writeWaiting(emptyList())
        Log.i(TAG, "Allowlist snapshot saved: ${pkgs.size} apps")
    }

    // ── New installs ──────────────────────────────────────────────────────────

    /**
     * Puts [pkg] on the waiting list and suspends it, if it is a genuinely new,
     * non-system app that is not already allowed / waiting / blocked.
     */
    suspend fun registerIfNew(pkg: String): Boolean = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext false
        if (pkg == context.packageName || pkg == "com.android.chrome" ||
            pkg.startsWith("com.android.") || pkg.startsWith("com.google.android.gms") ||
            SystemSafetyGuard.isPaused(context) ||
            SystemSafetyGuard.isDefaultCriticalApp(context, pkg)
        ) return@withContext false

        val pm = context.packageManager
        val info = try { pm.getApplicationInfo(pkg, 0) } catch (e: Exception) { return@withContext false }
        if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) return@withContext false

        if (isAllowed(pkg) || isWaiting(pkg)) return@withContext false
        if (BlockedAppsManager(context).isPackageBlocked(pkg)) return@withContext false
        if (try { LocalBlockedAppRepository(context).isAppBlocked(pkg) } catch (e: Exception) { false }) {
            return@withContext false
        }

        val name = try { pm.getApplicationLabel(info).toString() } catch (e: Exception) { pkg }
        synchronized(LOCK) {
            val list = readWaiting().toMutableList()
            if (list.none { it.packageName == pkg }) {
                list.add(WaitingApp(pkg, name, System.currentTimeMillis(), false))
                writeWaiting(list)
            }
        }
        setSuspended(pkg, true)
        Log.i(TAG, "New app on waiting list (suspended): $pkg")
        true
    }

    /** Catch-up scan: registers anything installed that is neither allowed, waiting nor blocked. */
    suspend fun scanForNewApps() {
        if (!isEnabled()) return
        val allowed = readAllowlist()
        val waiting = getWaiting().map { it.packageName }.toSet()
        context.packageManager.getInstalledApplications(0)
            .map { it.packageName }
            .filter { it !in allowed && it !in waiting }
            .forEach { registerIfNew(it) }
    }

    // ── User decisions ────────────────────────────────────────────────────────

    fun approve(pkg: String) = synchronized(LOCK) {
        writeWaiting(readWaiting().map {
            if (it.packageName == pkg) it.copy(approved = true) else it
        })
    }

    /** Only removes it from the waiting list. The UI adds it to the blocked list. */
    fun reject(pkg: String) = synchronized(LOCK) {
        writeWaiting(readWaiting().filter { it.packageName != pkg })
    }

    /**
     * Releases approved apps whose 12h are over (unsuspend + add to allowlist)
     * and drops waiting apps that were uninstalled. Returns released packages.
     */
    fun releaseDue(): List<String> = synchronized(LOCK) {
        val now = System.currentTimeMillis()
        val released = mutableListOf<String>()
        val keep = mutableListOf<WaitingApp>()
        val allowed = readAllowlist().toMutableSet()

        for (w in readWaiting()) {
            if (!isInstalled(w.packageName)) continue                 // uninstalled → forget
            if (w.approved && now >= w.installedAt + WAIT_MS) {
                setSuspended(w.packageName, false)
                allowed.add(w.packageName)
                released.add(w.packageName)
            } else keep.add(w)
        }
        if (released.isNotEmpty()) writeAllowlist(allowed)
        writeWaiting(keep)
        released
    }

    /** Fortress cleared: unsuspend waiting apps and wipe everything. */
    fun clearAll() = synchronized(LOCK) {
        readWaiting().forEach { setSuspended(it.packageName, false) }
        prefs.edit().clear().apply()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isInstalled(pkg: String) =
        try { context.packageManager.getApplicationInfo(pkg, 0); true } catch (e: Exception) { false }

    private fun setSuspended(pkg: String, suspended: Boolean) {
        try {
            dpm.setPackagesSuspended(admin, arrayOf(pkg), suspended)
        } catch (e: Exception) {
            Log.e(TAG, "setPackagesSuspended($pkg, $suspended) failed: ${e.message}")
        }
    }

    private fun readAllowlist(): Set<String> {
        val s = prefs.getString(KEY_ALLOWLIST, null) ?: return emptySet()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map { a.getString(it) }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    private fun writeAllowlist(pkgs: Set<String>) {
        val a = JSONArray(); pkgs.forEach { a.put(it) }
        prefs.edit().putString(KEY_ALLOWLIST, a.toString()).apply()
    }

    private fun readWaiting(): List<WaitingApp> {
        val s = prefs.getString(KEY_WAITING, null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                WaitingApp(
                    o.getString("pkg"), o.getString("name"),
                    o.getLong("at"), o.optBoolean("approved", false)
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun writeWaiting(list: List<WaitingApp>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject()
                .put("pkg", it.packageName).put("name", it.appName)
                .put("at", it.installedAt).put("approved", it.approved))
        }
        prefs.edit().putString(KEY_WAITING, a.toString()).apply()
    }
}
