package com.xbustcodex.termuxfilemanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson

data class VersionResponse(
    val latest_version: String,
    val update_url: String
)

class UpdateChecker(private val context: Context) {

    private val client = OkHttpClient()

    // Method to check for updates
    fun checkForUpdates() {
        // URL for the version check API
        val url = "https://xBustcodex.github.io/termux-file-manager/updates/version_json"
        
        // Create a request object
        val request = Request.Builder().url(url).build()

        // Make the request to check for updates
        Thread {
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                // Parse the JSON response
                val versionResponse = Gson().fromJson(responseBody, VersionResponse::class.java)

                // Compare the versions
                if (isNewerVersionAvailable(versionResponse.latest_version)) {
                    // If a new version is available, show the update dialog
                    showUpdateDialog(versionResponse.update_url)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Show an error message if there was an issue with the update check
                Toast.makeText(context, "Failed to check for updates", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    // Method to compare versions
    private fun isNewerVersionAvailable(latestVersion: String): Boolean {
        val currentVersion = BuildConfig.VERSION_NAME
        return currentVersion != latestVersion
    }

    // Method to show the update dialog
    private fun showUpdateDialog(updateUrl: String) {
        // Show an alert dialog asking the user if they want to update
        AlertDialog.Builder(context)
            .setTitle("Update Available")
            .setMessage("A new version is available. Do you want to update?")
            .setPositiveButton("Yes") { _, _ ->
                // Open the update URL
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
                context.startActivity(intent)
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
