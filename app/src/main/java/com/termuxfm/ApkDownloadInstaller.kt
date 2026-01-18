package com.termuxfm

import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.os.Build
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
            val apkUri = dm.getUriForDownloadedFile(downloadId)

            if (apkUri == null) {
                Toast.makeText(ctx, "Download finished but APK not found", Toast.LENGTH_LONG).show()
                return
            }

            // Android 8+ needs "Install unknown apps" permission per-app
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!ctx.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(ctx, "Allow 'Install unknown apps' to update", Toast.LENGTH_LONG).show()
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${ctx.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(settingsIntent)
                    return
                }
            }

            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
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
            setMimeType("application/vnd.android.package-archive")

            // DM will manage file access; destination is fine
            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)

            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        downloadId = dm.enqueue(request)

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
