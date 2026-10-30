package com.example.takwafortress.services.filtering

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Chrome site allowlist.
 *
 * - Before the fortress is activated: a site added here is allowed immediately.
 * - After activation (app allowlist snapshot exists): a newly added site is "waiting".
 *   It becomes allowed only when the user approved it AND [WAIT_MS] (12h) have passed.
 *   Reject = remove. Until then Chrome still blocks it.
 * - Once enforced, Chrome blocks everything except the allowed sites
 *   (SiteBlockingService.applyToChrome pushes URLBlocklist=* + URLAllowlist).
 */
class SiteAllowlistManager(context: Context) {

    data class AllowedSite(val domain: String, val effectiveAt: Long, val approved: Boolean = true) {
        fun isActive(now: Long = System.currentTimeMillis()) = approved && now >= effectiveAt
        fun remainingMs(now: Long = System.currentTimeMillis()) = (effectiveAt - now).coerceAtLeast(0L)
    }

    companion object {
        const val WAIT_MS = AppAllowlistManager.WAIT_MS
        private const val PREFS = "site_allowlist_config"
        private const val KEY_SITES = "sites"
        private const val KEY_APPLIED = "applied"
        private val LOCK = Any()
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val appAllowlist = AppAllowlistManager(context)

    /** Same switch as the app allowlist: on once the fortress has been activated. */
    fun isEnforced(): Boolean = appAllowlist.isEnabled()

    fun getAll(): List<AllowedSite> = synchronized(LOCK) { read().sortedBy { it.domain } }

    /** Adds a site (keeps the existing entry/timer if it is already there). */
    fun add(domain: String): AllowedSite = synchronized(LOCK) {
        val list = read().toMutableList()
        list.firstOrNull { it.domain == domain }?.let { return@synchronized it }
        val site = AllowedSite(
            domain,
            System.currentTimeMillis() + if (isEnforced()) WAIT_MS else 0L,
            approved = !isEnforced()   // after activation the user must approve it
        )
        list.add(site)
        write(list)
        site
    }

    fun approve(domain: String) = synchronized(LOCK) {
        write(read().map { if (it.domain == domain) it.copy(approved = true) else it })
    }

    /** Removing (or cancelling a pending) site only makes Chrome stricter, so it is always allowed. */
    fun remove(domain: String) = synchronized(LOCK) {
        write(read().filter { it.domain != domain })
    }

    fun effectiveDomains(): List<String> = synchronized(LOCK) {
        val now = System.currentTimeMillis()
        read().filter { it.isActive(now) }.map { it.domain }.sorted()
    }

    /** True when enforcement is on and Chrome's policy differs from the currently effective list. */
    fun needsApply(): Boolean {
        if (!isEnforced()) return false
        return effectiveDomains().joinToString(",") != prefs.getString(KEY_APPLIED, null)
    }

    fun markApplied() {
        prefs.edit().putString(KEY_APPLIED, effectiveDomains().joinToString(",")).apply()
    }

    fun clearAll() = synchronized(LOCK) { prefs.edit().clear().apply() }

    private fun read(): List<AllowedSite> {
        val s = prefs.getString(KEY_SITES, null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                AllowedSite(o.getString("domain"), o.getLong("at"), o.optBoolean("approved", true))
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun write(list: List<AllowedSite>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("domain", it.domain).put("at", it.effectiveAt).put("approved", it.approved)) }
        prefs.edit().putString(KEY_SITES, a.toString()).apply()
    }
}
