package com.example.takwafortress.repository.implementations

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.example.takwafortress.model.entities.BlockedSite
import java.util.UUID

class LocalBlockedSiteRepository(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "taqwa_blocked_sites_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY = "blocked_sites_json"
    }

    suspend fun getAll(): List<BlockedSite> = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            BlockedSite(
                id = obj.getString("id"),
                domain = obj.getString("domain"),
                displayLabel = obj.getString("displayLabel"),
                addedAt = obj.getLong("addedAt")
            )
        }
    }

    suspend fun add(domain: String): BlockedSite = withContext(Dispatchers.IO) {
        val site = BlockedSite(
            id = UUID.randomUUID().toString(),
            domain = domain,
            displayLabel = domain
        )
        val existing = getAll().toMutableList()
        // Don't add duplicates
        if (existing.any { it.domain == domain }) return@withContext site
        existing.add(site)
        save(existing)
        site
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        val updated = getAll().filter { it.id != id }
        save(updated)
    }

    suspend fun getAllDomains(): List<String> = getAll().map { it.domain }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY).apply()
    }

    private fun save(sites: List<BlockedSite>) {
        val arr = JSONArray()
        sites.forEach { site ->
            arr.put(JSONObject().apply {
                put("id", site.id)
                put("domain", site.domain)
                put("displayLabel", site.displayLabel)
                put("addedAt", site.addedAt)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}