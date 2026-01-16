package com.termuxfm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// Matches your version.json structure
data class VersionInfo(
    @SerializedName("latest_version") val latestVersion: String,
    @SerializedName("apk_url") val apkUrl: String,
    @SerializedName("changelog") val changelog: String? = null
)

private val httpClient = OkHttpClient()

suspend fun fetchUpdateInfo(): VersionInfo? = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("https://xbustcodex.github.io/termux-file-manager/updates/version.json")
        .build()

    try {
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w("UpdateChecker", "HTTP error: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            return@withContext Gson().fromJson(body, VersionInfo::class.java)
        }
    } catch (e: Exception) {
        Log.e("UpdateChecker", "Failed to check for updates", e)
        null
    }
}

// Get current app versionName (e.g. "3.0.0")
fun getCurrentAppVersion(context: Context): String {
    val pm = context.packageManager
    val pkg = context.packageName
    val info = pm.getPackageInfo(pkg, 0)
    return info.versionName ?: "0.0.0"
}

// Simple semantic version compare: returns true if remote > local
fun isNewerVersion(remote: String, local: String): Boolean {
    val r = remote.split(".")
    val l = local.split(".")

    val max = maxOf(r.size, l.size)
    for (i in 0 until max) {
        val rv = r.getOrNull(i)?.toIntOrNull() ?: 0
        val lv = l.getOrNull(i)?.toIntOrNull() ?: 0
        if (rv > lv) return true
        if (rv < lv) return false
    }
    return false
}

// Open APK URL in browser (or a download manager if you want later)
fun openUpdateUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
