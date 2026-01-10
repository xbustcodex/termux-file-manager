package com.termuxfm

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Root composable: holds current path, selected file, drawer and tools panel.
 */
@Composable
fun TermuxFileManagerApp(storage: StorageProvider) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentPath by remember { mutableStateOf("/") }
    var selectedFilePath by remember { mutableStateOf<String?>(null) }

    var showDrawer by remember { mutableStateOf(false) }
    var showToolsPanel by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }

    var lastFixResult by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {

        // Main area: either browser or editor
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
                onOpenDrawer = { showDrawer = true },
                onOpenTools = { showToolsPanel = true }
            )
        }

        // Drawer overlay (left)
        if (showDrawer) {
            // dimmed background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)
                    )
                    .clickable { showDrawer = false }
            )

            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.85f)
                    .align(Alignment.CenterStart),
                tonalElevation = 8.dp,
                shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
            ) {
                DrawerSheet(
                    currentPath = currentPath,
                    onNavigate = {
                        currentPath = it
                        showDrawer = false
                    },
                    onShowTools = {
                        showToolsPanel = true
                        showDrawer = false
                    },
                    onClose = { showDrawer = false }
                )
            }
        }

        // Tools panel overlay (right)
        if (showToolsPanel) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.82f)
                    .align(Alignment.CenterEnd),
                tonalElevation = 8.dp
            ) {
                ToolsPanel(
                    currentPath = currentPath,
                    lastFixResult = lastFixResult,
                    onClose = { showToolsPanel = false },
                    onFixPermissions = {
                        scope.launch {
                            try {
                                val fixed = fixScriptPermissionsForPath(storage, currentPath)
                                val msg =
                                    "Permissions fixed for $fixed items under $currentPath"
                                lastFixResult = msg
                                Toast
                                    .makeText(context, msg, Toast.LENGTH_LONG)
                                    .show()
                            } catch (e: Exception) {
                                Toast
                                    .makeText(
                                        context,
                                        e.message ?: "Failed to fix permissions",
                                        Toast.LENGTH_LONG
                                    )
                                    .show()
                            }
                        }
                    },
                    onCreateScriptFromTemplate = {
                        showTemplateDialog = true
                    }
                )
            }
        }

        // Template dialog (multi-language script generator)
        if (showTemplateDialog) {
            NamePromptDialog(
                title = "New script from template",
                hint = "myscript.sh / tool.py / runner.js / api.php",
                onDismiss = { showTemplateDialog = false },
                onConfirm = { rawName ->
                    scope.launch {
                        try {
                            val clean = rawName.trim().ifEmpty { "script.sh" }
                            val (finalName, body) = buildScriptTemplate(clean)

                            val newPath =
                                (currentPath.trimEnd('/') + "/$finalName").replace("//", "/")

                            storage.createFile(newPath)
                            storage.writeFile(newPath, body)

                            Toast
                                .makeText(
                                    context,
                                    "Created $finalName in $currentPath",
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        } catch (e: Exception) {
                            Toast
                                .makeText(
                                    context,
                                    e.message ?: "Failed to create script",
                                    Toast.LENGTH_LONG
                                )
                                .show()
                        } finally {
                            showTemplateDialog = false
                        }
                    }
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// SAF setup (unchanged)
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Main browser screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    storage: StorageProvider,
    path: String,
    onNavigate: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenTools: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialogFor by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteDialogFor by remember { mutableStateOf<FileItem?>(null) }

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
                    IconButton(onClick = onOpenTools) {
                        Text("\uD83D\uDD27") // hammer & wrench
                    }
                    IconButton(onClick = { refresh() }) {
                        Text("⟳")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // new folder
                Button(
                    onClick = { showNewFolderDialog = true },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("+📁")
                }
                // new file
                Button(
                    onClick = { showNewFileDialog = true },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("+📄")
                }
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
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
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            } else {
                val canGoUp = path != "/"
                if (canGoUp) {
                    FileRow(
                        item = FileItem(
                            name = "..",
                            path = path.trimEnd('/').substringBeforeLast("/", ""),
                            isDir = true
                        ),
                        onClick = {
                            val up = path.trimEnd('/').substringBeforeLast("/", "")
                            onNavigate(if (up.isBlank()) "/" else "/$up")
                        },
                        onRename = {},
                        onDelete = {}
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
    }

    // New file dialog
    if (showNewFileDialog) {
        NamePromptDialog(
            title = "New File",
            hint = "example.py",
            onDismiss = { showNewFileDialog = false },
            onConfirm = { name ->
                scope.launch {
                    try {
                        val newPath =
                            (path.trimEnd('/') + "/${name.trim()}").replace("//", "/")
                        storage.createFile(newPath)

                        // auto-shebang for empty new scripts
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

    // New folder dialog
    if (showNewFolderDialog) {
        NamePromptDialog(
            title = "New Folder",
            hint = "scripts",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                scope.launch {
                    try {
                        val newPath =
                            (path.trimEnd('/') + "/${name.trim()}").replace("//", "/")
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

    // Rename dialog
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

    // Delete dialog
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
                OutlinedButton(onClick = { showDeleteDialogFor = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Drawer + Tools panel UI
// ---------------------------------------------------------------------------

@Composable
private fun DrawerSheet(
    currentPath: String,
    onNavigate: (String) -> Unit,
    onShowTools: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            "Termux File Manager",
            style = MaterialTheme.typography.headlineSmall
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onNavigate("/") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50)
            ) {
                Text("🏠  Home")
            }
            OutlinedButton(
                onClick = { onNavigate("/Scripts") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50)
            ) {
                Text("📁  /Scripts")
            }
            OutlinedButton(
                onClick = { onNavigate("/") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50)
            ) {
                Text("📁  Workspace root")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tools", style = MaterialTheme.typography.titleMedium)
            Text(
                "\uD83D\uDD27 Show tools panel",
                modifier = Modifier.clickable { onShowTools() }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Other", style = MaterialTheme.typography.titleMedium)
            Text("ℹ About (coming soon)")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50)
        ) {
            Text("Close")
        }
    }
}

@Composable
private fun ToolsPanel(
    currentPath: String,
    lastFixResult: String?,
    onClose: () -> Unit,
    onFixPermissions: () -> Unit,
    onCreateScriptFromTemplate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tools", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Close",
                modifier = Modifier.clickable { onClose() },
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text("Quick utilities:", style = MaterialTheme.typography.bodyMedium)

        // Hex viewer (placeholder)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Hex viewer", style = MaterialTheme.typography.titleMedium)
            Text(
                "Inspect binary files in hex (coming soon)",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Permissions fixer
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Permissions fixer", style = MaterialTheme.typography.titleMedium)
            Text(
                "Batch-fix chmod for scripts under $currentPath",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = onFixPermissions,
                shape = RoundedCornerShape(50)
            ) {
                Text("Run permissions fixer")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // APK signer (placeholder)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("APK signer", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sign APKs directly from Termux storage (coming soon)",
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Log viewer (placeholder)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Log viewer", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tail & filter log files (coming soon)",
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Script templates
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Script templates", style = MaterialTheme.typography.titleMedium)
            Text(
                "Generate starter scripts for new tools",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                onClick = onCreateScriptFromTemplate,
                shape = RoundedCornerShape(50)
            ) {
                Text("Create script from template")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (lastFixResult != null) {
            Text(
                lastFixResult,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ---------------------------------------------------------------------------
// File rows + dialogs
// ---------------------------------------------------------------------------

@Composable
private fun FileRow(
    item: FileItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = (if (item.isDir) "📁 " else "📄 ") + item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.path,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Rename",
                modifier = Modifier.clickable { onRename() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Delete",
                modifier = Modifier.clickable { onDelete() }
            )
        }
    }
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

// ---------------------------------------------------------------------------
// Helpers: script detection, templates, permission fixer
// ---------------------------------------------------------------------------

private fun isScriptPath(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".sh") ||
        lower.endsWith(".bash") ||
        lower.endsWith(".py") ||
        lower.endsWith(".js") ||
        lower.endsWith(".php")
}

/**
 * Build a new script from a template.
 *
 * Uses detectScriptType/defaultShebangFor from Editor.kt.
 */
private fun buildScriptTemplate(rawName: String): Pair<String, String> {
    val type = detectScriptType(rawName)

    val fileName = when (type) {
        "bash" -> if (rawName.endsWith(".sh")) rawName else "$rawName.sh"
        "python" -> if (rawName.endsWith(".py")) rawName else "$rawName.py"
        "node" -> if (rawName.endsWith(".js")) rawName else "$rawName.js"
        "php" -> if (rawName.endsWith(".php")) rawName else "$rawName.php"
        else -> {
            // Unknown → default to bash script with .sh
            if (rawName.contains('.')) rawName else "$rawName.sh"
        }
    }

    val finalType = if (type == "unknown") "bash" else type
    val shebang = defaultShebangFor(finalType) ?: defaultShebangFor("bash")
        ?: "#!/data/data/com.termux/files/usr/bin/bash"

    val headerComment = when (finalType) {
        "bash" -> "# New bash script generated by Termux File Manager"
        "python" -> "# New Python script generated by Termux File Manager"
        "node" -> "// New Node.js script generated by Termux File Manager"
        "php" -> "// New PHP script generated by Termux File Manager"
        else -> "# New script generated by Termux File Manager"
    }

    val body = buildString {
        append(shebang)
        append("\n\n")
        append(headerComment)
        append("\n")
        when (finalType) {
            "python" -> append("\nif (__name__ == \"__main__\"):\n    print(\"Hello from $fileName\")\n")
            "node" -> append("\nconsole.log(\"Hello from $fileName\");\n")
            "php" -> append("\n// Your code here\n")
            else -> append("\n# Your code here\n")
        }
    }

    return fileName to body
}

/**
 * Simple permission fixer:
 * - Recursively walks under [rootPath] using StorageProvider
 * - For any script-like file, maps to an absolute path and sets +x
 */
private suspend fun fixScriptPermissionsForPath(
    storage: StorageProvider,
    rootPath: String
): Int {
    val scripts = mutableListOf<String>()

    suspend fun walk(path: String) {
        val children = storage.list(path)
        for (item in children) {
            if (item.isDir) {
                walk(item.path)
            } else if (isScriptPath(item.path)) {
                scripts += item.path
            }
        }
    }

    walk(rootPath)

    var fixed = 0
    for (logical in scripts) {
        val abs = resolveAbsolutePathForBrowser(storage, logical)
        if (abs != null) {
            try {
                val f = java.io.File(abs)
                if (f.exists()) {
                    if (f.setExecutable(true, false)) {
                        fixed++
                    }
                }
            } catch (_: Exception) {
                // ignore individual failures
            }
        }
    }
    return fixed
}

/**
 * Map logical paths used by StorageProvider to real filesystem paths.
 */
private fun resolveAbsolutePathForBrowser(
    storage: StorageProvider,
    logicalPath: String
): String? {
    return when (storage) {
        is SafStorageProvider ->
            "/data/data/com.termux/files/home$logicalPath"
        is LegacyFileStorageProvider ->
            "/sdcard/TermuxProjects$logicalPath"
        else -> null
    }
}
