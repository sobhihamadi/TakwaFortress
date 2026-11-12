package com.example.takwafortress.services.security

import android.content.Context
import android.util.Log
import com.example.takwafortress.services.filtering.ContentFilteringService

/**
 * Runs once after updating to the simplified version: unhides/unsuspends everything the
 * old version touched (fixes camera, gallery editor, boot-loop) and clears old restrictions.
 */
object LegacyRestore {
    private const val PREFS = "legacy_restore"
    private const val KEY = "done_v1"

    suspend fun runOnce(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean(KEY, false)) return
        try {
            val n = SystemSafetyGuard.restoreEverything(context)
            ContentFilteringService(context).releaseLegacyRestrictions()
            Log.i("LegacyRestore", "Restored $n packages")
            p.edit().putBoolean(KEY, true).apply()
        } catch (e: Exception) {
            Log.e("LegacyRestore", "failed: ${e.message}")
        }
    }
}
