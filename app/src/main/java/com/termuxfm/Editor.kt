package com.termuxfm

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(
    storage: StorageProvider,
    filePath: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    var showRunDialog by remember { mutableStateOf(false) }

    val fileName = remember(filePath) { filePath.trimEnd('/').substringAfterLast("/") }

    LaunchedEffect(filePath) {
        loading = true
        status = null
        try {
            content = storage.readFile(filePath)
        } catch (e: Exception) {
            status = "Read error: ${e.message}"
            content = ""
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                try {
                                    storage.writeFile(filePath, content)
                                    status = "Saved ✅"
                                } catch (e: Exception) {
                                    status = "Save error: ${e.message}"
                                }
                            }
                        }
                    ) { Text("Save") }

                    TextButton(
                        onClick = {
                            if (isRunnableScript(filePath)) {
                                showRunDialog = true
                            } else {
                                status = "Not a script file (.sh, .py, .bash, .js, .php)"
                            }
                        }
                    ) { Text("Run") }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (status != null) {
                Text(status!!)
            }

            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxSize(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                singleLine = false
            )
        }
    }

    // Run dialog
    if (showRunDialog) {
        val absPath = resolveScriptAbsolutePath(storage, filePath)
        val workDir = absPath?.substringBeforeLast('/', "/data/data/com.termux/files/home")

        if (absPath == null) {
            LaunchedEffect(filePath) {
                Toast.makeText(
                    context,
                    "Cannot resolve script path for this storage provider",
                    Toast.LENGTH_LONG
                ).show()
                showRunDialog = false
            }
        } else {
            AlertDialog(
                onDismissRequest = { showRunDialog = false },
                title = { Text("Run script") },
                text = { Text("What do you want to do with:\n$filePath") },
                confirmButton = {
                    TextButton(onClick = {
                        TermuxRunner.runScriptInTerminal(
                            context = context,
                            absolutePath = absPath,
                            workDir = workDir
                        )
                        showRunDialog = false
                    }) {
                        Text("Run in Termux")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            TermuxRunner.runScriptInBackground(
                                context = context,
                                absolutePath = absPath,
                                workDir = workDir
                            )
                            showRunDialog = false
                        }) {
                            Text("Run in background")
                        }
                        TextButton(onClick = { showRunDialog = false }) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }
    }
}

private fun isRunnableScript(path: String): Boolean {
    val lower = path.lowercase()
    return listOf(".sh", ".bash", ".py", ".js", ".php")
        .any { lower.endsWith(it) }
}

/**
 * Map logical file path to an absolute path Termux understands.
 *
 * - For SAF (Termux home): /data/data/com.termux/files/home + path
 * - For legacy sdcard workspace: /sdcard/TermuxProjects + path
 */
private fun resolveScriptAbsolutePath(
    storageProvider: StorageProvider,
    logicalPath: String
): String? {
    return when (storageProvider) {
        is SafStorageProvider ->
            "/data/data/com.termux/files/home$logicalPath"
        is LegacyFileStorageProvider ->
            "/sdcard/TermuxProjects$logicalPath"
        else -> null
    }
}
