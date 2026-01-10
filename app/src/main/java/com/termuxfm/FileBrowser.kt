package com.termuxfm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

// ---------------------------------------------------------
// Root app entry for the Compose UI
// ---------------------------------------------------------

@Composable
fun TermuxFileManagerApp(storage: StorageProvider) {
    var currentPath by remember { mutableStateOf("/") }
    var selectedFilePath by remember { mutableStateOf<String?>(null) }
    var hexViewerPath by remember { mutableStateOf<String?>(null) }
    var logViewerPath by remember { mutableStateOf<String?>(null) }

    when {
        // Log viewer has highest priority when active
        logViewerPath != null -> {
            LogViewerScreen(
                storage = storage,
                filePath = logViewerPath!!,
                onBack = { logViewerPath = null }
            )
        }

        // Hex viewer screen (you already wired this up)
        hexViewerPath != null -> {
            HexViewerScreen(
                storage = storage,
                filePath = hexViewerPath!!,
                onBack = { hexViewerPath = null }
            )
        }

        // Normal text/code editor
        selectedFilePath != null -> {
            EditorScreen(
                storage = storage,
                filePath = selectedFilePath!!,
                onBack = { selectedFilePath = null }
            )
        }

        // Default: file browser
        else -> {
            FileBrowserScreen(
                storage = storage,
                path = currentPath,
                onNavigate = { currentPath = it },
                onOpenFile = { selectedFilePath = it },
                onOpenHexViewer = { hexPath -> hexViewerPath = hexPath },
                onOpenLogViewer = { logPath -> logViewerPath = logPath }
            )
        }
    }
}


// ---------------------------------------------------------
// SAF setup
// ---------------------------------------------------------

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

// ---------------------------------------------------------
// Main file browser screen
// ---------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    storage: StorageProvider,
    path: String,
    onNavigate: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenHexViewer: (String) -> Unit,
    onOpenLogViewer: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialogFor by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteDialogFor by remember { mutableStateOf<FileItem?>(null) }

    var showDrawer by remember { mutableStateOf(false) }
    var showToolsPanel by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                items = storage.list(path)
            } catch (e: Exception) {
                error = e.message ?: "Error listing path"
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
                    TextButton(onClick = { showDrawer = true }) {
                        Text("≡")
                    }
                },
                actions = {
                    TextButton(onClick = { showToolsPanel = true }) {
                        Text("🛠")
                    }
                    TextButton(onClick = { refresh() }) {
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
    }

    // Drawer overlay
    if (showDrawer) {
        DrawerContent(
            currentPath = path,
            onNavigate = {
                onNavigate(it)
                showDrawer = false
            },
            onShowTools = {
                showDrawer = false
                showToolsPanel = true
            },
            onClose = { showDrawer = false }
        )
    }

    // Tools overlay panel
    if (showToolsPanel) {
        ToolsPanel(
            storage = storage,
            currentPath = path,
            onClose = { showToolsPanel = false },
            onOpenHexViewer = onOpenHexViewer,
            onOpenLogViewer = onOpenLogViewer,
            onOpenFile = onOpenFile
        )
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
                        val newPath = (path.trimEnd('/') + "/$name").replace("//", "/")
                        storage.createFile(newPath)

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
                OutlinedButton(onClick = { showDeleteDialogFor = null }) { Text("Cancel") }
            }
        )
    }
}

// ---------------------------------------------------------
// Drawer (left-side menu)
// ---------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerContent(
    currentPath: String,
    onNavigate: (String) -> Unit,
    onShowTools: () -> Unit,
    onClose: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "Termux File Manager",
                style = MaterialTheme.typography.headlineSmall
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onNavigate("/") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🏠  Home")
                }
                OutlinedButton(
                    onClick = { onNavigate("/Scripts") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📁  /Scripts")
                }
                OutlinedButton(
                    onClick = { onNavigate("/") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📁  Workspace root")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tools", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onShowTools) {
                    Text("🛠  Show tools panel")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Other", style = MaterialTheme.typography.titleMedium)
                Text("ℹ About (coming soon)")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close")
            }
        }
    }
}

// ---------------------------------------------------------
// Tools panel (right-side overlay)
// ---------------------------------------------------------

@Composable
private fun ToolsPanel(
    storage: StorageProvider,
    currentPath: String,
    onClose: () -> Unit,
    onOpenHexViewer: (String) -> Unit,
    onOpenLogViewer: (String) -> Unit,
    onOpenFile: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var runningFix by remember { mutableStateOf(false) }

    var showHexPrompt by remember { mutableStateOf(false) }
    var showTemplatePrompt by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.9f)
            .padding(start = 56.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tools", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onClose) { Text("Close") }
            }

            Text("Quick utilities:", style = MaterialTheme.typography.bodyMedium)

            // Hex viewer
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Hex viewer", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Inspect binary files in hex.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = { showHexPrompt = true }) {
                    Text("Open file in hex viewer")
                }
            }

            // Permissions fixer
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Permissions fixer", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Batch-fix chmod for scripts under $currentPath",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    enabled = !runningFix,
                    onClick = {
                        scope.launch {
                            runningFix = true
                            status = "Running permissions fixer..."
                            val count = try {
                                fixScriptPermissionsForPath(storage, currentPath)
                            } catch (e: Exception) {
                                status = "Error: ${e.message}"
                                runningFix = false
                                return@launch
                            }
                            status = "Permissions fixed for $count items under $currentPath"
                            runningFix = false
                        }
                    }
                ) {
                    Text(if (runningFix) "Working..." else "Run permissions fixer")
                }
            }

            // APK signer placeholder
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("APK signer", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Sign APKs directly from Termux storage (coming soon)",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Log viewer placeholder
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Log viewer", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tail & filter log files (coming soon)",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Script templates
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Script templates", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Generate starter scripts for new tools.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = { showTemplatePrompt = true }) {
                    Text("Create script from template")
                }
            }

            Spacer(Modifier.height(8.dp))

            if (status != null) {
                Text(
                    status!!,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    // Prompt: hex viewer file name
    if (showHexPrompt) {
        NamePromptDialog(
            title = "Hex viewer",
            hint = "myscript.sh / tool.bin",
            onDismiss = { showHexPrompt = false },
            onConfirm = { name ->
                val cleaned = name.trim()
                if (cleaned.isNotEmpty()) {
                    val logicalPath =
                        (currentPath.trimEnd('/') + "/$cleaned").replace("//", "/")
                    onOpenHexViewer(logicalPath)
                    showHexPrompt = false
                    onClose()
                }
            }
        )
    }

    // Prompt: script template
    if (showTemplatePrompt) {
        NamePromptDialog(
            title = "New script from template",
            hint = "myscript.sh / tool.py / runner.js / api.php",
            onDismiss = { showTemplatePrompt = false },
            onConfirm = { name ->
                val cleaned = name.trim()
                if (cleaned.isNotEmpty()) {
                    val logicalPath =
                        (currentPath.trimEnd('/') + "/$cleaned").replace("//", "/")
                    val template = buildScriptTemplate(cleaned)

                    scope.launch {
                        try {
                            storage.createFile(logicalPath)
                        } catch (_: Exception) {
                            // file may already exist – overwrite
                        }
                        storage.writeFile(logicalPath, template)
                        onOpenFile(logicalPath)
                    }

                    showTemplatePrompt = false
                    onClose()
                }
            }
        )
    }
}

// ---------------------------------------------------------
// Hex viewer screen
// ---------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexViewerScreen(
    storage: StorageProvider,
    filePath: String,
    onBack: () -> Unit
) {
    var hexDump by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    val fileName = remember(filePath) { filePath.trimEnd('/').substringAfterLast("/") }

    LaunchedEffect(filePath, storage) {
        loading = true
        error = null
        try {
            val abs = resolveAbsolutePathForStorage(storage, filePath)
                ?: throw IllegalStateException("Cannot resolve real path for $filePath")

            val bytes = withContext(Dispatchers.IO) {
                File(abs).readBytes()
            }

            val maxBytes = 64 * 1024 // 64 KiB
            val limit = min(bytes.size, maxBytes)
            var dump = toHexDump(bytes, limit)
            if (bytes.size > maxBytes) {
                dump += "\n... (truncated, file is ${bytes.size} bytes; showing first $maxBytes bytes)\n"
            }
            hexDump = dump
        } catch (e: Exception) {
            error = "Error reading file: ${e.message}"
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hex: $fileName") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(12.dp)
        ) {
            if (error != null) {
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (hexDump != null) {
                val scrollState = rememberScrollState()
                Text(
                    text = hexDump!!,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                )
            }
        }
    }
}

// ---------------------------------------------------------
// Simple file row + dialogs
// ---------------------------------------------------------

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

// ---------------------------------------------------------
// Helpers: permissions fixer, hex dump, path resolution, templates
// ---------------------------------------------------------

private suspend fun fixScriptPermissionsForPath(
    storage: StorageProvider,
    rootPath: String
): Int = withContext(Dispatchers.IO) {
    val scripts = mutableListOf<String>()
    collectScripts(storage, rootPath, scripts)
    var fixed = 0

    for (logical in scripts) {
        val abs = resolveAbsolutePathForStorage(storage, logical) ?: continue
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $abs"))
            val code = proc.waitFor()
            if (code == 0) fixed++
        } catch (_: Exception) {
            // ignore failures per-file
        }
    }
    fixed
}

private suspend fun collectScripts(
    storage: StorageProvider,
    path: String,
    out: MutableList<String>
) {
    val entries = storage.list(path)
    for (item in entries) {
        if (item.isDir) {
            collectScripts(storage, item.path, out)
        } else if (isScriptPath(item.path)) {
            out += item.path
        }
    }
}

private fun isScriptPath(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".sh") ||
            lower.endsWith(".bash") ||
            lower.endsWith(".py") ||
            lower.endsWith(".js") ||
            lower.endsWith(".php")
}

private fun resolveAbsolutePathForStorage(
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

private fun toHexDump(bytes: ByteArray, limit: Int, bytesPerLine: Int = 16): String {
    val sb = StringBuilder()
    var offset = 0
    while (offset < limit) {
        val lineEnd = min(offset + bytesPerLine, limit)
        sb.append(String.format("%08X  ", offset))

        for (i in offset until offset + bytesPerLine) {
            if (i < lineEnd) {
                sb.append(String.format("%02X ", bytes[i]))
            } else {
                sb.append("   ")
            }
            if (i == offset + 7) sb.append(' ')
        }

        sb.append(" ")

        for (i in offset until lineEnd) {
            val b = bytes[i].toInt() and 0xFF
            val ch = if (b in 0x20..0x7E) b.toChar() else '.'
            sb.append(ch)
        }

        sb.append('\n')
        offset += bytesPerLine
    }
    return sb.toString()
}

private fun buildScriptTemplate(fileName: String): String {
    val type = detectScriptType(fileName)
    val shebang = defaultShebangFor(type) ?: "#!/usr/bin/env $type"
    val base = fileName.substringAfterLast('/')

    return when (type) {
        "bash" -> """
            $shebang

            # $base - generated by Termux File Manager
            # TODO: implement your tool here

            main() {
                echo "Hello from $base"
            }

            main "${'$'}@"
        """.trimIndent()

        "python" -> """
            $shebang

            \"\"\"$base - generated by Termux File Manager\"\"\"

            import sys


            def main(argv: list[str]) -> int:
                print("Hello from $base")
                return 0


            if __name__ == "__main__":
                raise SystemExit(main(sys.argv[1:]))
        """.trimIndent()

        "node" -> """
            $shebang

            /**
             * $base - generated by Termux File Manager
             */

            function main(argv) {
              console.log("Hello from $base");
            }

            main(process.argv.slice(2));
        """.trimIndent()

        "php" -> """
            $shebang
            <?php
            /**
             * $base - generated by Termux File Manager
             */

            function main(array ${'$'}argv): int {
                echo "Hello from $base\n";
                return 0;
            }

            exit(main(array_slice(${'$'}argv, 1)));
        """.trimIndent()

        else -> """
            $shebang

            # $base - generated by Termux File Manager
            # TODO: implement your tool here
        """.trimIndent()
    }
}
