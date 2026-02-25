package com.example.takwafortress.services.core

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import android.content.Intent
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class VersionInfo(
    val versionCode: Long = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val forceUpdate: Boolean = false,
    val releaseNotes: String = ""
)

class UpdateChecker(private val context: Context) {

    companion object {
        private const val TAG = "UPDATE_DEBUG"
        private const val APK_FILE_NAME = "takwa_update.apk"
    }

    private val firestore = FirebaseFirestore.getInstance()

    // ── Version check ─────────────────────────────────────────────────────────

    suspend fun checkForUpdate(): VersionInfo? {
        return try {
            Log.d(TAG, "Checking for update... current=${getCurrentVersionCode()}")

            val doc = withTimeoutOrNull(10_000) {
                firestore
                    .collection("app_config")
                    .document("version")
                    .get(Source.SERVER)
                    .await()
            }

            if (doc == null) {
                Log.w(TAG, "Timed out — offline, skipping")
                return null
            }
            if (!doc.exists()) {
                Log.w(TAG, "app_config/version not found in Firestore")
                return null
            }

            val remote  = doc.getLong("version_code") ?: 0L
            val current = getCurrentVersionCode()
            Log.d(TAG, "Remote=$remote  Current=$current")

            if (remote > current) {
                Log.d(TAG, "✅ Update available: ${doc.getString("version_name")}")
                VersionInfo(
                    versionCode  = remote,
                    versionName  = doc.getString("version_name") ?: "",
                    apkUrl       = doc.getString("apk_url") ?: "",
                    forceUpdate  = doc.getBoolean("force_update") ?: false,
                    releaseNotes = doc.getString("release_notes") ?: ""
                )
            } else {
                Log.d(TAG, "App is up to date")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed (likely offline): ${e.message}")
            null
        }
    }

    private fun getCurrentVersionCode(): Long {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
            }
        } catch (e: Exception) { 0L }
    }

    // ── Update dialog with inline progress ───────────────────────────────────

    fun showUpdateDialog(activity: Activity, info: VersionInfo) {
        if (activity.isFinishing || activity.isDestroyed) return

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 40, 64, 16)
        }

        val messageText = TextView(activity).apply {
            text = "A new version is required to continue.\n\nWhat's new:\n${info.releaseNotes}"
            textSize = 14f
            setPadding(0, 0, 0, 32)
        }

        val progressBar = ProgressBar(
            activity, null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
            isIndeterminate = false
            visibility = View.GONE
        }

        val statusText = TextView(activity).apply {
            textSize = 13f
            setPadding(0, 12, 0, 0)
            visibility = View.GONE
        }

        layout.addView(messageText)
        layout.addView(progressBar)
        layout.addView(statusText)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("🆕  ${info.versionName} Available")
            .setView(layout)
            .setPositiveButton("Update Now", null)
            .setCancelable(false)
            .create()

        dialog.setOnKeyListener { _, keyCode, _ ->
            keyCode == android.view.KeyEvent.KEYCODE_BACK
        }

        dialog.setOnShowListener {
            val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            btn.setOnClickListener {
                btn.isEnabled          = false
                btn.text               = "Downloading…"
                progressBar.visibility = View.VISIBLE
                statusText.visibility  = View.VISIBLE
                statusText.text        = "Starting download…"

                startDownload(
                    activity    = activity,
                    apkUrl      = info.apkUrl,
                    versionName = info.versionName,
                    onProgress  = { pct ->
                        activity.runOnUiThread {
                            progressBar.progress = pct
                            statusText.text      = "Downloading… $pct%"
                        }
                    },
                    onComplete  = { apkFile ->
                        activity.runOnUiThread {
                            progressBar.progress = 100
                            statusText.text      = "Installing…"
                            btn.text             = "Installing…"
                        }
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(400)
                            dialog.dismiss()
                            installApk(activity, apkFile)
                        }
                    },
                    onError     = { err ->
                        activity.runOnUiThread {
                            progressBar.visibility = View.GONE
                            statusText.text        = "❌ Download failed: $err\nTap Retry to try again."
                            btn.isEnabled          = true
                            btn.text               = "Retry"
                        }
                    }
                )
            }
        }

        dialog.show()
    }

    // ── Download via DownloadManager ─────────────────────────────────────────

    private fun startDownload(
        activity: Activity,
        apkUrl: String,
        versionName: String,
        onProgress: (Int) -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // ✅ FIX 1: Use getExternalFilesDir properly — no Uri.fromFile needed
            val destDir  = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(destDir, APK_FILE_NAME)
            if (destFile.exists()) destFile.delete()

            val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            // ✅ FIX 2: GitHub URLs redirect (302). We must follow the redirect manually
            // to get the real CDN URL, then pass that to DownloadManager.
            // We do this by resolving the redirect on an IO coroutine first.
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val resolvedUrl = resolveRedirectUrl(apkUrl)
                    Log.d(TAG, "Resolved URL: $resolvedUrl")

                    val request = DownloadManager.Request(Uri.parse(resolvedUrl)).apply {
                        setTitle("Taqwa Fortress $versionName")
                        setDescription("Downloading update…")

                        // ✅ FIX 3: VISIBILITY_HIDDEN causes "Invalid value for visibility: 2"
                        // on many devices. Use VISIBILITY_VISIBLE instead.
                        setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE
                        )

                        // ✅ FIX 4: Use setDestinationInExternalFilesDir instead of
                        // setDestinationUri(Uri.fromFile(...)) — works correctly on Android 10+
                        setDestinationInExternalFilesDir(
                            activity,
                            Environment.DIRECTORY_DOWNLOADS,
                            APK_FILE_NAME
                        )

                        setAllowedOverMetered(true)
                        setAllowedOverRoaming(true)

                        // ✅ FIX 5: Add required headers so GitHub CDN accepts the request
                        addRequestHeader("User-Agent", "Mozilla/5.0")
                        addRequestHeader("Accept", "application/octet-stream")
                    }

                    val downloadId = dm.enqueue(request)
                    Log.d(TAG, "Download enqueued id=$downloadId url=$resolvedUrl")

                    // Poll progress every 500ms
                    var running = true
                    while (running) {
                        delay(500)

                        val cursor = dm.query(
                            DownloadManager.Query().setFilterById(downloadId)
                        ) ?: continue

                        if (!cursor.moveToFirst()) { cursor.close(); continue }

                        val status     = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total      = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val reason     = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        cursor.close()

                        when (status) {
                            DownloadManager.STATUS_RUNNING,
                            DownloadManager.STATUS_PENDING -> {
                                if (total > 0) {
                                    onProgress(((downloaded * 100) / total).toInt())
                                }
                            }
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                running = false
                                Log.d(TAG, "✅ Download complete")
                                onProgress(100)
                                onComplete(destFile)
                            }
                            DownloadManager.STATUS_FAILED -> {
                                running = false
                                Log.e(TAG, "❌ Download failed, reason=$reason")
                                onError(getDownloadErrorReason(reason))
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Download coroutine exception: ${e.message}", e)
                    onError(e.message ?: "unknown error")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download setup exception: ${e.message}", e)
            onError(e.message ?: "unknown error")
        }
    }

    /**
     * Follows HTTP redirects (like GitHub's 302 → CDN URL) and returns the final URL.
     * DownloadManager does NOT follow redirects on its own, so we must resolve first.
     */
    private fun resolveRedirectUrl(url: String): String {
        var currentUrl = url
        var redirectCount = 0
        val maxRedirects = 5

        while (redirectCount < maxRedirects) {
            val connection = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 8_000
            connection.readTimeout    = 8_000
            connection.connect()

            val responseCode = connection.responseCode
            Log.d(TAG, "Redirect check [$redirectCount]: $currentUrl → $responseCode")

            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location") ?: break
                connection.disconnect()
                currentUrl = location
                redirectCount++
            } else {
                connection.disconnect()
                break
            }
        }

        return currentUrl
    }

    /**
     * Translates DownloadManager error codes into readable messages.
     */
    private fun getDownloadErrorReason(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME       -> "Cannot resume download"
        DownloadManager.ERROR_DEVICE_NOT_FOUND    -> "Storage not found"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
        DownloadManager.ERROR_FILE_ERROR          -> "Storage error"
        DownloadManager.ERROR_HTTP_DATA_ERROR     -> "HTTP data error"
        DownloadManager.ERROR_INSUFFICIENT_SPACE  -> "Not enough storage space"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS  -> "Too many redirects"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP code"
        DownloadManager.ERROR_UNKNOWN             -> "Unknown error"
        else                                      -> "Error code $reason"
    }

    // ── Install ───────────────────────────────────────────────────────────────

    private fun installApk(activity: Activity, apkFile: File) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.provider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }

            activity.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )

            Log.d(TAG, "Install intent launched")

        } catch (e: Exception) {
            Log.e(TAG, "Install failed: ${e.message}", e)
        }
    }
}