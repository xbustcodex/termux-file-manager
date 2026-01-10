package com.termuxfm

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun TermuxFileManagerApp(storage: StorageProvider) {
    var currentPath by remember { mutableStateOf("/") }
    var selectedFilePath by remember { mutableStateOf<String?>(null) }

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
            onOpenFile = { selectedFilePath = it }
        )
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    storage: StorageProvider,
    path: String,
    onNavigate: (String) -> Unit,
    onOpenFile: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialogFor by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteDialogFor by remember { mutableStateOf<FileItem?>(null) }

    // UI state for side panels
    var showNavDrawer by remember { mutableStateOf(false) }
    var showToolsPanel by remember { mutableStateOf(false) }

    // Status text for tools (e.g. permissions fixer)
    var toolsStatus by remember { mutableStateOf<String?>(null) }

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
                    IconButton(onClick = { showNavDrawer = true }) {
                        Text("☰")
                    }
                },
                actions = {
                    IconButton(onClick = { showToolsPanel = true }) {
                        Text("🛠️")
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
            // Main file list
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                        CircularProgressIndicator()
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
                        Divider()
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
                            Divider()
                        }
                    }
                }
            }

            // Left nav "drawer" overlay
            if (showNavDrawer) {
                Surface(
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(280.dp)
                ) {
                    NavDrawerContent(
                        currentPath = path,
                        onNavigate = {
                            onNavigate(it)
                            showNavDrawer = false
                        },
                        onShowTools = {
                            showToolsPanel = true
                            showNavDrawer = false
                        },
                        onDismiss = { showNavDrawer = false }
                    )
                }
            }

            // Right tools panel overlay
            if (showToolsPanel) {
                Surface(
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd)
                        .width(320.dp)
                ) {
                    ToolsPanel(
                        currentPath = path,
                        status = toolsStatus,
                        onClose = { showToolsPanel = false },
                        onFixPermissions = {
                            scope.launch {
                                // actual implementation of Permissions fixer
                                if (!RootCheck.hasRoot()) {
                                    toolsStatus = "Root not available. Cannot chmod files."
                                    return@launch
                                }
                                toolsStatus = "Running permissions fix on $path …"

                                val fixedCount = runPermissionsFixer(storage, path)
                                toolsStatus =
                                    "Permissions fixed for $fixedCount items under $path"
                            }
                        },
                        onToolComingSoon = { toolName ->
                            toolsStatus = "$toolName: coming soon"
                        }
                    )
                }
            }
        }
    }

    // --- dialogs for file / folder operations --------------------------------

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
                        // Auto-shebang for empty new scripts
                        val type = detectScriptType(newPath)
                        val shebang = defaultShebangFor(type)
                        if (shebang != null) {
                            storage.writeFile(newPath, shebang + "\n\n")
                        }

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
}

// -----------------------------------------------------------------------------
// Navigation drawer content
// -----------------------------------------------------------------------------

@Composable
private fun NavDrawerContent(
    currentPath: String,
    onNavigate: (String) -> Unit,
    onShowTools: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Termux File Manager", style = MaterialTheme.typography.headlineSmall)

        // Quick locations
        Surface(
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .clickable {
                        onNavigate("/")
                        onDismiss()
                    }
                    .padding(16.dp)
            ) {
                Text("🏠 Home", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(8.dp))

        Text("Favorites", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "📁 /Scripts",
                modifier = Modifier.clickable {
                    onNavigate("/Scripts")
                    onDismiss()
                }
            )
            Text(
                "📁 Workspace root",
                modifier = Modifier.clickable {
                    onNavigate("/")
                    onDismiss()
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        Text("Tools", style = MaterialTheme.typography.titleSmall)
        Text(
            "🛠️ Show tools panel",
            modifier = Modifier.clickable {
                onShowTools()
            }
        )

        Spacer(Modifier.height(16.dp))

        Text("Other", style = MaterialTheme.typography.titleSmall)
        Text("ℹ About (coming soon)")
    }
}

// -----------------------------------------------------------------------------
// Tools panel content (RIGHT SIDE) – with real Permissions fixer
// -----------------------------------------------------------------------------

@Composable
private fun ToolsPanel(
    currentPath: String,
    status: String?,
    onClose: () -> Unit,
    onFixPermissions: () -> Unit,
    onToolComingSoon: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Tools", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onClose) {
                Text("Close")
            }
        }

        Text(
            "Quick utilities:",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(8.dp))

        // Hex viewer (stub)
        ToolEntry(
            title = "Hex viewer",
            subtitle = "Inspect binary files in hex (coming soon)"
        ) { onToolComingSoon("Hex viewer") }

        // Permissions fixer – REAL IMPLEMENTATION
        ToolEntry(
            title = "Permissions fixer",
            subtitle = "Batch-fix chmod for scripts under $currentPath"
        ) { onFixPermissions() }

        // APK signer (stub)
        ToolEntry(
            title = "APK signer",
            subtitle = "Sign APKs directly from Termux storage (coming soon)"
        ) { onToolComingSoon("APK signer") }

        // Log viewer (stub)
        ToolEntry(
            title = "Log viewer",
            subtitle = "Tail & filter log files (coming soon)"
        ) { onToolComingSoon("Log viewer") }

        // Script templates (stub)
        ToolEntry(
            title = "Script templates",
            subtitle = "Generate starter scripts for new tools (coming soon)"
        ) { onToolComingSoon("Script templates") }

        Spacer(Modifier.height(12.dp))

        if (status != null) {
            Divider()
            Text(
                status,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ToolEntry(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// -----------------------------------------------------------------------------
// File rows, dialogs, helpers
// -----------------------------------------------------------------------------

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
                TextButton(onClick = onRename) { Text("Rename") }
                TextButton(onClick = onDelete) { Text("Delete") }
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

// -----------------------------------------------------------------------------
// Permissions fixer engine (uses root + shell chmod)
// -----------------------------------------------------------------------------

/**
 * Recursively walk the current logical path and chmod files/dirs via root.
 *
 * - SAF mode: assumes Termux home at /data/data/com.termux/files/home
 * - LEGACY mode: assumes workspace at /sdcard/TermuxProjects
 *
 * Returns number of items successfully chmod'ed.
 */
private suspend fun runPermissionsFixer(
    storage: StorageProvider,
    logicalRoot: String
): Int {
    val baseAbs = when (storage) {
        is SafStorageProvider -> "/data/data/com.termux/files/home"
        is LegacyFileStorageProvider -> "/sdcard/TermuxProjects"
        else -> return 0
    }

    suspend fun walk(path: String): Int {
        val list = try {
            storage.list(path)
        } catch (_: Exception) {
            return 0
        }

        var count = 0
        for (item in list) {
            val abs = (baseAbs + item.path).replace("//", "/")
            val mode = if (item.isDir) "775" else "664"
            if (runSuChmod(abs, mode)) count++
            if (item.isDir) {
                count += walk(item.path)
            }
        }
        return count
    }

    // Try chmod on the root itself (as directory)
    val rootAbs = (baseAbs + logicalRoot).replace("//", "/")
    runSuChmod(rootAbs, "775")

    return walk(logicalRoot)
}

private fun runSuChmod(absPath: String, mode: String): Boolean {
    return try {
        val cmd = "chmod $mode '$absPath'"
        val proc = ProcessBuilder("su", "-c", cmd)
            .redirectErrorStream(true)
            .start()
        proc.waitFor() == 0
    } catch (_: Exception) {
        false
    }
}
