package com.termuxfm

import android.app.DownloadManager
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat

class ApkDownloadInstaller(private val context: Context) {

    private var downloadId: Long = -1L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != downloadId) return

            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val apkUrl = dm.getUriForDownloadedFile(downloadId)

            if (apkUrl == null) {
                Toast.makeText(ctx, "Download finished but APK not found", Toast.LENGTH_LONG).show()
                return
            }

            // Launch installer
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                ctx.startActivity(installIntent)
            } catch (e: Exception) {
                Toast.makeText(ctx, "Could not open installer: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun downloadAndInstall(apkUrl: String, fileName: String = "app-update.apk") {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Unregister any previous receiver safely
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}

        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("Termux File Manager update")
            setDescription("Downloading update…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            // Works well across Android versions; DM handles scoped storage internally
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

            // Helps with GitHub/CDN redirects + caching behavior
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        downloadId = dm.enqueue(request)

        // Register receiver (Android 13+ needs export flag)
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

        Toast.makeText(context, "Downloading update…", Toast.LENGTH_SHORT).show()
    }
}
