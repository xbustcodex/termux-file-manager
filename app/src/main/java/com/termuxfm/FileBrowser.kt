package com.termuxfm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// -------------------------------------------------------------
// Root composable
// -------------------------------------------------------------

@Composable
fun TermuxFileManagerApp(storage: StorageProvider) {
    var currentPath by remember { mutableStateOf("/") }
    var selectedFilePath by remember { mutableStateOf<String?>(null) }
    var showToolsPanel by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                currentPath = currentPath,
                onClose = { scope.launch { drawerState.close() } },
                onGoHome = {
                    currentPath = "/"
                    selectedFilePath = null
                    scope.launch { drawerState.close() }
                },
                onGoScripts = {
                    currentPath = "/Scripts"
                    selectedFilePath = null
                    scope.launch { drawerState.close() }
                },
                onGoWorkspaceRoot = {
                    currentPath = "/"
                    selectedFilePath = null
                    scope.launch { drawerState.close() }
                },
                onShowTools = {
                    showToolsPanel = true
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        if (selectedFilePath != null) {
            EditorScreen(
                storage = storage,
                filePath = selectedFilePath!!,
                onBack = { selectedFilePath = null }
            )
        } else {
            FileBrowserScreen(
                storage = storage,
                path = currentPath,
                onNavigate = { currentPath = it },
                onOpenFile = { selectedFilePath = it },
                onOpenDrawer = { scope.launch { drawerState.open() } },
                showToolsPanel = showToolsPanel,
                onShowToolsPanel = { showToolsPanel = true },
                onHideToolsPanel = { showToolsPanel = false }
            )
        }
    }
}

// -------------------------------------------------------------
// SAF setup screen (unchanged)
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafSetupScreen(
    onPick: () -> Unit,
    onUseLegacy: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Setup") })
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Root detected. To manage Termux files using the system folder picker, grant access to your Termux folder.",
                style = MaterialTheme.typography.bodyLarge
            )
            Button(onClick = onPick) {
                Text("Pick Termux Folder (SAF)")
            }
            OutlinedButton(onClick = onUseLegacy) {
                Text("Use /sdcard/TermuxProjects instead")
            }
            Text(
                "Tip: If Termux home isn't selectable on your device, use the /sdcard workspace. Termux can still run scripts from there.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// -------------------------------------------------------------
// Main file browser + tools panel
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    storage: StorageProvider,
    path: String,
    onNavigate: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    showToolsPanel: Boolean,
    onShowToolsPanel: () -> Unit,
    onHideToolsPanel: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialogFor by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteDialogFor by remember { mutableStateOf<FileItem?>(null) }

    // tools panel state
    var permissionsMessage by remember { mutableStateOf<String?>(null) }
    var showTemplateDialog by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                items = storage.list(path)
            } catch (e: Exception) {
                error = e.message ?: "Error"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(path, storage) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Path: $path",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Text("≡")
                    }
                },
                actions = {
                    IconButton(onClick = { onShowToolsPanel() }) {
                        Text("\uD83D\uDD28") // hammer & wrench
                    }
                    IconButton(onClick = { refresh() }) {
                        Text("⟳")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FloatingActionButton(onClick = { showNewFolderDialog = true }) {
                    Text("+📁")
                }
                FloatingActionButton(onClick = { showNewFileDialog = true }) {
                    Text("+📄")
                }
            }
        }
    ) { pad ->
        Box(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
        ) {

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if (error != null) {
                    Text(
                        "Error: $error",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LinearProgressIndicator()
                    }
                } else {
                    val canGoUp = path != "/"
                    if (canGoUp) {
                        ListItem(
                            headlineContent = { Text("..") },
                            supportingContent = { Text("Up") },
                            modifier = Modifier
                                .clickable {
                                    val up = path.trimEnd('/').substringBeforeLast("/", "")
                                    onNavigate(if (up.isBlank()) "/" else "/$up")
                                }
                                .padding(horizontal = 4.dp)
                        )
                    }

                    LazyColumn(Modifier.fillMaxSize()) {
                        items(items) { item ->
                            FileRow(
                                item = item,
                                onClick = {
                                    if (item.isDir) onNavigate(item.path) else onOpenFile(item.path)
                                },
                                onRename = { showRenameDialogFor = item },
                                onDelete = { showDeleteDialogFor = item }
                            )
                        }
                    }
                }
            }

            // --- Right tools panel overlay ---
            if (showToolsPanel) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                        ),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Surface(
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(min = 260.dp, max = 360.dp)
                    ) {
                        ToolsPanel(
                            currentPath = path,
                            permissionsMessage = permissionsMessage,
                            onClose = onHideToolsPanel,
                            onFixPermissions = {
                                scope.launch {
                                    val fixed = fixScriptPermissionsForPath(storage, path)
                                    permissionsMessage =
                                        if (fixed >= 0) {
                                            "Permissions fixed for $fixed items under $path"
                                        } else {
                                            "Permissions fixer not supported for this storage"
                                        }
                                }
                            },
                            onCreateScriptTemplate = {
                                showTemplateDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // --- dialogs ---------------------------------------------------------

    if (showNewFileDialog) {
        NamePromptDialog(
            title = "New File",
            hint = "example.py",
            onDismiss = { showNewFileDialog = false },
            onConfirm = { name ->
                scope.launch {
                    try {
                        val newPath = (path.trimEnd('/') + "/$name").replace("//", "/")
                        storage.createFile(newPath)
                        refresh()
                    } catch (e: Exception) {
                        error = e.message ?: "Failed to create file"
                    } finally {
                        showNewFileDialog = false
                    }
                }
            }
        )
    }

    if (showNewFolderDialog) {
        NamePromptDialog(
            title = "New Folder",
            hint = "scripts",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                scope.launch {
                    try {
                        val newPath = (path.trimEnd('/') + "/$name").replace("//", "/")
                        storage.createFolder(newPath)
                        refresh()
                    } catch (e: Exception) {
                        error = e.message ?: "Failed to create folder"
                    } finally {
                        showNewFolderDialog = false
                    }
                }
            }
        )
    }

    if (showRenameDialogFor != null) {
        val item = showRenameDialogFor!!
        NamePromptDialog(
            title = "Rename",
            hint = item.name,
            initial = item.name,
            onDismiss = { showRenameDialogFor = null },
            onConfirm = { newName ->
                scope.launch {
                    try {
                        storage.rename(item.path, newName)
                        refresh()
                    } catch (e: Exception) {
                        error = e.message ?: "Rename failed"
                    } finally {
                        showRenameDialogFor = null
                    }
                }
            }
        )
    }

    if (showDeleteDialogFor != null) {
        val item = showDeleteDialogFor!!
        AlertDialog(
            onDismissRequest = { showDeleteDialogFor = null },
            title = { Text("Delete") },
            text = { Text("Delete '${item.name}'? This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            storage.delete(item.path)
                            refresh()
                        } catch (e: Exception) {
                            error = e.message ?: "Delete failed"
                        } finally {
                            showDeleteDialogFor = null
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialogFor = null }) { Text("Cancel") }
            }
        )
    }

    if (showTemplateDialog) {
        NamePromptDialog(
            title = "New bash script from template",
            hint = "myscript.sh",
            onDismiss = { showTemplateDialog = false },
            onConfirm = { rawName ->
                scope.launch {
                    try {
                        val clean = rawName.trim().ifEmpty { "script.sh" }
                        val name = if (clean.endsWith(".sh")) clean else "$clean.sh"
                        val newPath = (path.trimEnd('/') + "/$name").replace("//", "/")

                        val body =
                            "#!/data/data/com.termux/files/usr/bin/bash\n\n" +
                                "# New Termux script: $name\n" +
                                "# Generated by Termux File Manager\n\n"

                        storage.createFile(newPath)
                        storage.writeFile(newPath, body)
                        refresh()
                    } catch (e: Exception) {
                        error = e.message ?: "Failed to create script"
                    } finally {
                        showTemplateDialog = false
                    }
                }
            }
        )
    }
}

// -------------------------------------------------------------
// Drawer & tools panel
// -------------------------------------------------------------

@Composable
private fun DrawerContent(
    currentPath: String,
    onClose: () -> Unit,
    onGoHome: () -> Unit,
    onGoScripts: () -> Unit,
    onGoWorkspaceRoot: () -> Unit,
    onShowTools: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 260.dp, max = 360.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Termux File Manager", style = MaterialTheme.typography.titleLarge)

            Spacer(Modifier.height(8.dp))

            Button(onClick = { onGoHome() }, modifier = Modifier.fillMaxWidth()) {
                Text("🏠  Home")
            }

            OutlinedButton(onClick = { onGoScripts() }, modifier = Modifier.fillMaxWidth()) {
                Text("📁  /Scripts")
            }

            OutlinedButton(onClick = { onGoWorkspaceRoot() }, modifier = Modifier.fillMaxWidth()) {
                Text("📂  Workspace root")
            }

            Spacer(Modifier.height(16.dp))
            Text("Tools", style = MaterialTheme.typography.titleMedium)
            Text(
                modifier = Modifier
                    .clickable { onShowTools() }
                    .padding(vertical = 8.dp),
                text = "🔧 Show tools panel"
            )

            Spacer(Modifier.height(16.dp))
            Text("Other", style = MaterialTheme.typography.titleMedium)
            Text("ℹ About (coming soon)", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun ToolsPanel(
    currentPath: String,
    permissionsMessage: String?,
    onClose: () -> Unit,
    onFixPermissions: () -> Unit,
    onCreateScriptTemplate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tools", style = MaterialTheme.typography.titleLarge)
            Text(
                modifier = Modifier.clickable { onClose() },
                text = "Close",
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(8.dp))
        Text("Quick utilities:", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(8.dp))

        // Hex viewer (placeholder)
        Text("Hex viewer", style = MaterialTheme.typography.titleMedium)
        Text(
            "Inspect binary files in hex (coming soon)",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(12.dp))

        // Permissions fixer
        Text("Permissions fixer", style = MaterialTheme.typography.titleMedium)
        Text(
            "Batch-fix chmod for scripts under $currentPath",
            style = MaterialTheme.typography.bodySmall
        )
        Button(onClick = onFixPermissions) {
            Text("Run permissions fixer")
        }

        Spacer(Modifier.height(12.dp))

        // APK signer (placeholder)
        Text("APK signer", style = MaterialTheme.typography.titleMedium)
        Text(
            "Sign APKs directly from Termux storage (coming soon)",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(12.dp))

        // Log viewer (placeholder)
        Text("Log viewer", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tail & filter log files (coming soon)",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(12.dp))

        // Script templates (real)
        Text("Script templates", style = MaterialTheme.typography.titleMedium)
        Text(
            "Generate starter scripts for new tools",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(onClick = onCreateScriptTemplate) {
            Text("Create bash script from template")
        }

        Spacer(Modifier.height(16.dp))

        if (permissionsMessage != null) {
            Text(
                text = permissionsMessage,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// -------------------------------------------------------------
// Helpers
// -------------------------------------------------------------

@Composable
private fun FileRow(
    item: FileItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                (if (item.isDir) "📁 " else "📄 ") + item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(item.path, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    modifier = Modifier.clickable { onRename() },
                    text = "Rename",
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    modifier = Modifier.clickable { onDelete() },
                    text = "Delete",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
private fun NamePromptDialog(
    title: String,
    hint: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(hint) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val name = text.trim()
                    if (name.isNotEmpty()) onConfirm(name)
                }
            ) { Text("OK") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// Simple extension used only in this file to avoid clashes with Editor.kt
private fun isScriptPath(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".sh") ||
        lower.endsWith(".bash") ||
        lower.endsWith(".py") ||
        lower.endsWith(".js") ||
        lower.endsWith(".php")
}

private fun resolveAbsoluteForTools(
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

/**
 * Run chmod 700 on script-y files under the given path.
 * Returns count of files successfully updated, or -1 if not supported.
 */
private suspend fun fixScriptPermissionsForPath(
    storage: StorageProvider,
    path: String
): Int {
    val baseItems = try {
        storage.list(path)
    } catch (e: Exception) {
        return -1
    }

    val scripts = baseItems.filter { !it.isDir && isScriptPath(it.path) }

    var fixed = 0
    for (item in scripts) {
        val abs = resolveAbsoluteForTools(storage, item.path) ?: continue
        val cmd = "chmod 700 '$abs'"
        try {
            val p = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val code = p.waitFor()
            if (code == 0) fixed++
        } catch (_: Exception) {
            // ignore failures per-file
        }
    }
    return fixed
}
