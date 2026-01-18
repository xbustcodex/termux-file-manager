package com.termuxfm

import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat

class ApkDownloadInstaller(private val context: Context) {

    private var downloadId: Long = -1L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != downloadId) return

            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val q = DownloadManager.Query().setFilterById(downloadId)
            val c = dm.query(q)
            if (!c.moveToFirst()) {
                Toast.makeText(ctx, "Download not found", Toast.LENGTH_LONG).show()
                c.close()
                return
            }

            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                c.close()
                Toast.makeText(ctx, "Download failed (reason=$reason)", Toast.LENGTH_LONG).show()
                return
            }

            c.close()

            val apkUri = dm.getUriForDownloadedFile(downloadId)
            if (apkUri == null) {
                Toast.makeText(ctx, "Downloaded but APK URI is null", Toast.LENGTH_LONG).show()
                return
            }

            // Android 8+ requires "Install unknown apps" permission per-app
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!ctx.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(ctx, "Allow 'Install unknown apps' for this app", Toast.LENGTH_LONG).show()
                    val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${ctx.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(i)
                    return
                }
            }

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                ctx.startActivity(installIntent)
            } catch (e: Exception) {
                Toast.makeText(ctx, "Installer failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun downloadAndInstall(apkUrl: String, fileName: String = "app-update.apk") {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}

        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("Termux File Manager update")
            setDescription("Downloading update…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)

            // ✅ Safer than public Downloads (avoids permission/storage edge cases)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)

            // Helps some servers
            setMimeType("application/vnd.android.package-archive")
        }

        try {
            downloadId = dm.enqueue(request)
            registerReceiverSafe()
            Toast.makeText(context, "Downloading update…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download start failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun registerReceiverSafe() {
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
    }
}
