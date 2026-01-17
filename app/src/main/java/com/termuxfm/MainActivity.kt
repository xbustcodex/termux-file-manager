package com.termuxfm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQ_STORAGE_PERMS = 1001
    }

    private val pickTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                persistTreePermission(this, uri)
                // UI will recompose and pick up saved URI
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔐 Ask for storage permissions / all-files access
        ensureStoragePermissions()

        setContent {
            MaterialTheme {
                Surface {
                    AppRoot(
                        onPickSafFolder = { pickTree.launch(null) }
                    )
                }
            }
        }
    }

    /**
     * Ask for the right kind of storage access on each Android version.
     */
    private fun ensureStoragePermissions() {
        // Android 11+ (R, SDK 30): MANAGE_EXTERNAL_STORAGE ("All files access")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(
                    this,
                    "Please grant 'All files access' so Termux File Manager can see all storage.",
                    Toast.LENGTH_LONG
                ).show()

                val uri = Uri.parse("package:$packageName")
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    uri
                )
                startActivity(intent)
            }
        } else {
            // Android 6–10: classic READ/WRITE_EXTERNAL_STORAGE runtime permissions
            val needed = mutableListOf<String>()

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.READ_EXTERNAL_STORAGE
            }

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.WRITE_EXTERNAL_STORAGE
            }

            if (needed.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    needed.toTypedArray(),
                    REQ_STORAGE_PERMS
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_STORAGE_PERMS) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (!granted) {
                Toast.makeText(
                    this,
                    "Storage permissions denied – some features may not work.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

@Composable
private fun AppRoot(onPickSafFolder: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf(chooseWorkspaceConfig(context)) }

    // --- UPDATE SYSTEM STATE ---
    var updateInfo by remember { mutableStateOf<VersionInfo?>(null) }
    var updateCheckDone by remember { mutableStateOf(false) }

    // Ensure legacy workspace exists (safe even if we don't use it)
    LaunchedEffect(Unit) {
        ensureLegacyWorkspaceExists(config.legacyRootPath)
    }

    // Refresh config if SAF gets saved
    LaunchedEffect(Unit) {
        // simple refresh on first render; deeper refresh happens when activity returns
        config = chooseWorkspaceConfig(context)
    }

    // ---- CHECK FOR UPDATE ON STARTUP ----
    LaunchedEffect(Unit) {
        val info = fetchUpdateInfo()
        if (info != null) {
            val current = getCurrentAppVersion(context)
            if (isNewerVersion(info.latestVersion, current)) {
                updateInfo = info
            }
        }
        updateCheckDone = true
    }

    // ---- SHOW UPDATE DIALOG IF NEEDED ----
    if (updateInfo != null) {
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("Update available") },
            text = {
                Text(
                    buildString {
                        append("A newer version (${updateInfo!!.latestVersion}) is available.\n")
                        append("You are currently on ${getCurrentAppVersion(context)}.\n\n")
                        updateInfo!!.changelog?.let {
                            append("Changes:\n$it")
                        }
                     }
                 )
             },
             confirmButton = {
                 TextButton(
                     onClick = {
                         val installer = ApkDownloadInstaller(context)
                         installer.downloadAndInstall(
                             apkUrl = updateInfo!!.apkUrl,
                             fileName = "TermuxFileManager-${updateInfo!!.latestVersion}.apk"
                         )
                         updateInfo = null
                     }
                 ) {
                     Text("Update")
                 }
             },
             dismissButton = {
                 TextButton(onClick = { updateInfo = null }) {
                     Text("Later")
                 }
             }
        )
    }

    // ---- EXISTING STORAGE / UI LOGIC ----
    if (config.mode == WorkspaceMode.SAF && config.safTreeUri == null) {

        SafSetupScreen(
            onPick = { onPickSafFolder() },
            onUseLegacy = {
                config = config.copy(mode = WorkspaceMode.LEGACY_SD)
            }
        )

    } else {

        val storage: StorageProvider = remember(config) {
            when (config.mode) {
                WorkspaceMode.SAF ->
                    SafStorageProvider(context, config.safTreeUri!!)
                WorkspaceMode.LEGACY_SD ->
                    LegacyFileStorageProvider(config.legacyRootPath)
            }
        }

        TermuxFileManagerApp(storage = storage)
    }
}
