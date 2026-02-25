package com.example.takwafortress.services.core

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class VersionInfo(
    val versionCode: Long = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val forceUpdate: Boolean = false,
    val releaseNotes: String = ""
)

class UpdateChecker(private val context: android.content.Context) {

    private val firestore = FirebaseFirestore.getInstance()

    suspend fun checkForUpdate(): VersionInfo? {
        return try {
            val doc = firestore
                .collection("app_config")
                .document("version")
                .get()
                .await()

            val remoteVersionCode = doc.getLong("version_code") ?: 0L
            val currentVersionCode = getCurrentVersionCode()

            if (remoteVersionCode > currentVersionCode) {
                VersionInfo(
                    versionCode  = remoteVersionCode,
                    versionName  = doc.getString("version_name") ?: "",
                    apkUrl       = doc.getString("apk_url") ?: "",
                    forceUpdate  = doc.getBoolean("force_update") ?: false,
                    releaseNotes = doc.getString("release_notes") ?: ""
                )
            } else null

        } catch (e: Exception) {
            null
        }
    }

    private fun getCurrentVersionCode(): Long {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .longVersionCode
            } else {
                @Suppress("DEPRECATION")
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .versionCode.toLong()
            }
        } catch (e: Exception) { 0L }
    }

    fun showUpdateDialog(activity: Activity, info: VersionInfo) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("New Version Available — ${info.versionName}")
            .setMessage("What's new:\n${info.releaseNotes}\n\nPlease update to continue.")
            .setPositiveButton("Download Update") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl))
                activity.startActivity(intent)
            }
            .setCancelable(!info.forceUpdate)
            .create()

        dialog.show()
    }
}