package com.termuxfm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pickTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                persistTreePermission(this, uri)
                // UI will recompose and pick up saved URI
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                        openUpdateUrl(context, updateInfo!!.apkUrl)
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
        // Rooted device, but no SAF permission yet
        SafSetupScreen(
            onPick = { onPickSafFolder() },
            onUseLegacy = {
                // fall back to legacy even on rooted
                config = config.copy(mode = WorkspaceMode.LEGACY_SD)
            }
        )
        return
    }

    val storage: StorageProvider = remember(config) {
        when (config.mode) {
            WorkspaceMode.SAF -> SafStorageProvider(context, config.safTreeUri)
            WorkspaceMode.LEGACY_SD -> LegacyFileStorageProvider(config.legacyRootPath)
        }
    }

    TermuxFileManagerApp(storage = storage)
}
