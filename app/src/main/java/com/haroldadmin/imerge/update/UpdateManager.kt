package com.haroldadmin.imerge.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import com.haroldadmin.imerge.BuildConfig
import com.haroldadmin.imerge.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val changelog: String,
)

data class DownloadEnqueueResult(val id: Long, val startedNew: Boolean)

class UpdateManager(private val context: Context) {
    private val downloadManager = context.getSystemService(DownloadManager::class.java)
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val manifestUrl = BuildConfig.UPDATE_MANIFEST_URL
        if (!manifestUrl.startsWith("https://")) return@withContext null

        val connection = URL(manifestUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "iMerge/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode !in 200..299) return@withContext null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val info = UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = json.getString("apkUrl"),
                sha256 = json.getString("sha256").lowercase(),
                changelog = json.optString("changelog"),
            )
            val minSdk = json.optInt("minSdk", 29)
            info.takeIf {
                it.versionCode > BuildConfig.VERSION_CODE &&
                    minSdk <= android.os.Build.VERSION.SDK_INT &&
                    it.apkUrl.startsWith("https://") &&
                    SHA_256_REGEX.matches(it.sha256)
            }
        } finally {
            connection.disconnect()
        }
    }

    fun enqueue(info: UpdateInfo): DownloadEnqueueResult {
        val existingId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        val existingVersion = preferences.getInt(KEY_VERSION_CODE, -1)
        if (existingId >= 0 && existingVersion == info.versionCode && isActiveOrComplete(existingId)) {
            return DownloadEnqueueResult(existingId, startedNew = false)
        }
        if (existingId >= 0) downloadManager.remove(existingId)

        val fileName = "iMerge-${info.versionCode}.apk"
        updateFile(fileName).delete()
        val request = DownloadManager.Request(info.apkUrl.toUri())
            .setTitle("iMerge ${info.versionName}")
            .setDescription(context.getString(R.string.update_notification_description))
            .setMimeType(APK_MIME)
            // The user only consents to a background download, so never spend mobile data on it.
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "updates/$fileName",
            )
        val id = downloadManager.enqueue(request)
        preferences.edit {
            putLong(KEY_DOWNLOAD_ID, id)
            putInt(KEY_VERSION_CODE, info.versionCode)
            putString(KEY_VERSION_NAME, info.versionName)
            putString(KEY_FILE_NAME, fileName)
            putString(KEY_SHA_256, info.sha256)
        }
        return DownloadEnqueueResult(id, startedNew = true)
    }

    fun skipVersion(versionCode: Int) {
        preferences.edit { putInt(KEY_SKIPPED_VERSION, versionCode) }
    }

    fun isSkipped(versionCode: Int): Boolean =
        preferences.getInt(KEY_SKIPPED_VERSION, -1) == versionCode

    fun isPendingDownload(id: Long): Boolean = preferences.getLong(KEY_DOWNLOAD_ID, -1L) == id

    suspend fun completedApk(): File? = withContext(Dispatchers.IO) {
        val id = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        val targetVersion = preferences.getInt(KEY_VERSION_CODE, -1)
        if (targetVersion <= BuildConfig.VERSION_CODE) {
            clearDownloadedUpdate(id)
            return@withContext null
        }
        if (id < 0 || downloadStatus(id) != DownloadManager.STATUS_SUCCESSFUL) return@withContext null

        val fileName = preferences.getString(KEY_FILE_NAME, null) ?: return@withContext null
        val expectedHash = preferences.getString(KEY_SHA_256, null) ?: return@withContext null
        val file = updateFile(fileName)
        if (!file.isFile || file.sha256() != expectedHash) {
            clearDownloadedUpdate(id)
            return@withContext null
        }
        file
    }

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun isActiveOrComplete(id: Long): Boolean = when (downloadStatus(id)) {
        DownloadManager.STATUS_PENDING,
        DownloadManager.STATUS_RUNNING,
        DownloadManager.STATUS_PAUSED,
        DownloadManager.STATUS_SUCCESSFUL -> true
        else -> false
    }

    private fun downloadStatus(id: Long): Int {
        downloadManager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return -1
            return cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }
    }

    private fun clearDownloadedUpdate(id: Long) {
        preferences.getString(KEY_FILE_NAME, null)?.let(::updateFile)?.delete()
        if (id >= 0) downloadManager.remove(id)
        preferences.edit {
            remove(KEY_DOWNLOAD_ID)
            remove(KEY_VERSION_CODE)
            remove(KEY_VERSION_NAME)
            remove(KEY_FILE_NAME)
            remove(KEY_SHA_256)
        }
    }

    private fun updateFile(name: String): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates/$name")

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFS_NAME = "app_update"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_VERSION_CODE = "version_code"
        private const val KEY_VERSION_NAME = "version_name"
        private const val KEY_FILE_NAME = "file_name"
        private const val KEY_SHA_256 = "sha_256"
        private const val KEY_SKIPPED_VERSION = "skipped_version"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private val SHA_256_REGEX = Regex("^[0-9a-f]{64}$")
    }
}
