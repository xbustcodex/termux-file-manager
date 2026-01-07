package com.termuxfm

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

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

    var config by remember { mutableStateOf(chooseWorkspaceConfig(context)) }

    // Ensure legacy workspace exists (safe even if we don't use it)
    LaunchedEffect(Unit) {
        ensureLegacyWorkspaceExists(config.legacyRootPath)
    }

    // Refresh config if SAF gets saved
    LaunchedEffect(Unit) {
        // simple refresh on first render; deeper refresh happens when activity returns
        config = chooseWorkspaceConfig(context)
    }

    if (config.mode == WorkspaceMode.SAF && config.safTreeUri == null) {
        // Rooted device, but no SAF permission yet
        SafSetupScreen(
            onPick = {
                onPickSafFolder()
            },
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
            WorkspaceMode.LEGACY_SD -> SdcardStorageProvider()
        }
    }

    TermuxFileManagerApp(storage = storage)
}

