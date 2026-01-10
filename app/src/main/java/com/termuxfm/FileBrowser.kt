package com.termuxfm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/* -----------------------------------------------------------------------
 * Root app switcher: browser / editor / hex viewer
 * -------------------------------------------------------------------- */

@Composable
fun TermuxFileManagerApp(storage: StorageProvider) {
    var currentPath by remember { mutableStateOf("/") }
    var editorPath by remember { mutableStateOf<String?>(null) }
    var hexPath by remember { mutableStateOf<String?>(null) }

    when {
        editorPath != null -> {
            EditorScreen(
                storage = storage,
                filePath = editorPath!!,
                onBack = { editorPath = null }
            )
        }

        hexPath != null -> {
            HexViewerScreen(
                storage = storage,
                filePath = hexPath!!,
                onBack = { hexPath = null }
            )
        }

        else -> {
            FileBrowserScreen(
                storage = storage,
                path = currentPath,
                onNavigate = { currentPath = it },
                onOpenFile = { editorPath = it },
                onOpenHex = { hexPath = it }
            )
        }
    }
}

/* -----------------------------------------------------------------------
 * SAF setup screen
 * -------------------------------------------------------------------- */

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

/* -----------------------------------------------------------------------
 * Main file browser + side menu + tools panel
 * -------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    storage: StorageProvider,
    path: String,
    onNavigate: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenHex: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialogFor by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteDialogFor by remember { mutableStateOf<FileItem?>(null) }

    var showNavSheet by remember { mutableStateOf(false) }
    var showToolsPanel by remember { mutableStateOf(false) }
    var toolsStatus by remember { mutableStateOf<String?>(null) }

    var showHexFilePicker by remember { mutableStateOf(false) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var pendingTemplate by remember { mutableStateOf<ScriptTemplateInfo?>(null) }
    var showTemplateNameDialog by remember { mutableStateOf(false) }

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
                    TextButton(onClick = { showNavSheet = true }) {
                        Text("☰")
                    }
                },
                actions = {
                    IconButton(onClick = { showToolsPanel = true }) {
                        Text("🛠")
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
                                onDelete = { showDeleteDialogFor = item },
                                onHexView = {
                                    if (!item.isDir) {
                                        onOpenHex(item.path)
                                    }
                                }
                            )
                            Divider()
                        }
                    }
                }
            }

            // Left nav sheet
            if (showNavSheet) {
                Surface(
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(280.dp)
                        .align(Alignment.CenterStart)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Termux File Manager",
                            style = MaterialTheme.typography.titleLarge
                        )

                        // Quick paths
                        Surface(
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showNavSheet = false
                                    onNavigate("/")
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🏠 Home")
                            }
                        }

                        Text(
                            "/Scripts",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showNavSheet = false
                                    onNavigate("/Scripts")
                                }
                        )

                        Text(
                            "Workspace root",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showNavSheet = false
                                    onNavigate("/")
                                }
                        )

                        Spacer(Modifier.height(16.dp))

                        Text("Tools", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "🛠 Show tools panel",
                            modifier = Modifier
                                .clickable {
                                    showNavSheet = false
                                    showToolsPanel = true
                                }
                                .padding(vertical = 4.dp)
                        )

                        Spacer(Modifier.height(16.dp))
                        Text("Other", style = MaterialTheme.typography.titleMedium)
                        Text("ℹ About (coming soon)")
                    }
                }
            }

            // Right tools panel
            if (showToolsPanel) {
                Surface(
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp)
                        .align(Alignment.CenterEnd)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tools", style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { showToolsPanel = false }) {
                                Text("Close")
                            }
                        }

                        Text("Quick utilities:")

                        // Hex viewer
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showToolsPanel = false
                                    showHexFilePicker = true
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Text("Hex viewer", style = MaterialTheme.typography.titleMedium)
                            Text("Open any file in a hex/ASCII viewer")
                        }

                        // Permissions fixer
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        toolsStatus = "Fixing permissions under $path..."
                                        val count = fixScriptPermissionsForPath(storage, path)
                                        toolsStatus = "Permissions fixed for $count items under $path"
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Text("Permissions fixer", style = MaterialTheme.typography.titleMedium)
                            Text("Batch-fix chmod for scripts in this folder (uses su)")
                        }

                        // APK signer – roadmap only
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text("APK signer", style = MaterialTheme.typography.titleMedium)
                            Text("Sign APKs directly from Termux storage (coming soon)")
                        }

                        // Log viewer – roadmap only
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text("Log viewer", style = MaterialTheme.typography.titleMedium)
                            Text("Tail & filter log files (coming soon)")
                        }

                        // Script templates – working
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showToolsPanel = false
                                    showTemplatePicker = true
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Text("Script templates", style = MaterialTheme.typography.titleMedium)
                            Text("Generate starter scripts for new tools")
                        }

                        Spacer(Modifier.height(12.dp))

                        if (toolsStatus != null) {
                            Divider()
                            Text(toolsStatus!!)
                        }
                    }
                }
            }

            // Pick file for hex viewer
            if (showHexFilePicker) {
                AlertDialog(
                    onDismissRequest = { showHexFilePicker = false },
                    title = { Text("Open in Hex viewer") },
                    text = {
                        val fileItems = items.filter { !it.isDir }
                        if (fileItems.isEmpty()) {
                            Text("No files in this folder.")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                fileItems.forEach { f ->
                                    Text(
                                        text = f.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showHexFilePicker = false
                                                onOpenHex(f.path)
                                            }
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showHexFilePicker = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            // Script template picker
            if (showTemplatePicker) {
                AlertDialog(
                    onDismissRequest = { showTemplatePicker = false },
                    title = { Text("Script templates") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SCRIPT_TEMPLATES.forEach { template ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            pendingTemplate = template
                                            showTemplatePicker = false
                                            showTemplateNameDialog = true
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(template.title, style = MaterialTheme.typography.titleMedium)
                                    Text(template.description)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showTemplatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Script template filename prompt
            if (showTemplateNameDialog && pendingTemplate != null) {
                val tmpl = pendingTemplate!!
                NamePromptDialog(
                    title = "New ${tmpl.title}",
                    hint = tmpl.defaultName,
                    initial = tmpl.defaultName,
                    onDismiss = {
                        showTemplateNameDialog = false
                        pendingTemplate = null
                    },
                    onConfirm = { name ->
                        scope.launch {
                            try {
                                val newPath = (path.trimEnd('/') + "/$name").replace("//", "/")
                                storage.createFile(newPath)
                                val content = buildTemplateContent(tmpl, name)
                                storage.writeFile(newPath, content)
                                refresh()
                                onOpenFile(newPath)
                            } catch (e: Exception) {
                                error = e.message ?: "Failed to create template"
                            } finally {
                                showTemplateNameDialog = false
                                pendingTemplate = null
                            }
                        }
                    }
                )
            }
        }
    }

    // New file / folder / rename / delete dialogs (unchanged behaviour)

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

/* -----------------------------------------------------------------------
 * File row + dialogs
 * -------------------------------------------------------------------- */

@Composable
private fun FileRow(
    item: FileItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onHexView: () -> Unit
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
                if (!item.isDir) {
                    TextButton(onClick = onHexView) { Text("Hex") }
                }
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

/* -----------------------------------------------------------------------
 * Hex viewer screen
 * -------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexViewerScreen(
    storage: StorageProvider,
    filePath: String,
    onBack: () -> Unit
) {
    var hexLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val fileName = remember(filePath) { filePath.trimEnd('/').substringAfterLast("/") }

    LaunchedEffect(filePath, storage) {
        loading = true
        error = null
        try {
            val bytes: ByteArray? = when (storage) {
                is LegacyFileStorageProvider -> storage.readBinary(filePath)
                is SafStorageProvider -> storage.readBinary(filePath)
                else -> null
            }

            if (bytes == null) {
                error = "Could not read file bytes"
                hexLines = emptyList()
            } else {
                hexLines = formatHexLines(bytes)
            }
        } catch (e: Exception) {
            error = e.message ?: "Hex view error"
            hexLines = emptyList()
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
                .padding(8.dp)
        ) {
            if (error != null) {
                Text(
                    "Error: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(hexLines) { line ->
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

private fun formatHexLines(bytes: ByteArray): List<String> {
    val lines = mutableListOf<String>()
    var offset = 0
    while (offset < bytes.size) {
        val end = min(offset + 16, bytes.size)
        val slice = bytes.sliceArray(offset until end)

        val hexPart = slice.joinToString(" ") { "%02X".format(it) }
        val paddedHex = hexPart.padEnd(16 * 3 - 1, ' ')

        val asciiPart = slice.map { b ->
            val c = b.toInt() and 0xFF
            if (c in 32..126) c.toChar() else '.'
        }.joinToString("")

        val line = "%08X  %s  %s".format(offset, paddedHex, asciiPart)
        lines.add(line)

        offset += 16
    }
    return lines
}

/* -----------------------------------------------------------------------
 * Script templates
 * -------------------------------------------------------------------- */

data class ScriptTemplateInfo(
    val id: String,
    val title: String,
    val description: String,
    val defaultName: String
)

private val SCRIPT_TEMPLATES = listOf(
    ScriptTemplateInfo(
        id = "bash_tool",
        title = "Bash tool (Termux)",
        description = "Termux-ready bash script with banner & argument stub.",
        defaultName = "tool.sh"
    ),
    ScriptTemplateInfo(
        id = "python_cli",
        title = "Python CLI",
        description = "Python script with argparse and main() entry.",
        defaultName = "tool.py"
    ),
    ScriptTemplateInfo(
        id = "node_cli",
        title = "Node.js CLI",
        description = "Node.js script with argument parsing.",
        defaultName = "tool.js"
    ),
    ScriptTemplateInfo(
        id = "hid_stub",
        title = "HID payload stub",
        description = "Starter HID-style bash script for keyboard payloads.",
        defaultName = "payload.sh"
    )
)

private fun buildTemplateContent(template: ScriptTemplateInfo, fileName: String): String {
    val baseName = fileName.substringBeforeLast('.')

    return when (template.id) {
        "bash_tool" -> """
            #!/data/data/com.termux/files/usr/bin/bash
            # ${baseName} - Bash tool generated by Termux File Manager
            # Author: ${"xBusterCodex"}
            # Date: $(date +"%Y-%m-%d")
            
            VERSION="1.0.0"
            
            banner() {
                clear
                echo "========================================"
                echo "  ${baseName} v$VERSION"
                echo "  (generated by Termux File Manager)"
                echo "========================================"
                echo
            }
            
            usage() {
                banner
                echo "Usage: ${baseName}.sh [options]"
                echo
                echo "Options:"
                echo "  -h, --help    Show this help"
                echo
                exit 1
            }
            
            main() {
                banner
                # TODO: add your logic here
                echo "Hello from ${baseName}!"
            }
            
            case "$1" in
                -h|--help) usage ;;
                *) main "$@" ;;
            esac
        """.trimIndent()

        "python_cli" -> """
            #!/data/data/com.termux/files/usr/bin/python
            """
                "\"\"\"${baseName} - Python CLI generated by Termux File Manager.\"\"\""
            """
            
            import argparse
            
            
            def build_parser() -> argparse.ArgumentParser:
                parser = argparse.ArgumentParser(description="${baseName} CLI tool")
                # parser.add_argument("--option", help="Example option")
                return parser
            
            
            def main() -> None:
                parser = build_parser()
                args = parser.parse_args()
                # TODO: implement tool logic here
                print("Hello from ${baseName}!")
            
            
            if __name__ == "__main__":
                main()
        """.trimIndent()

        "node_cli" -> """
            #!/data/data/com.termux/files/usr/bin/node
            /**
             * ${baseName} - Node.js CLI generated by Termux File Manager
             */
            
            function main() {
              const args = process.argv.slice(2);
              // TODO: parse args & implement logic
              console.log("Hello from ${baseName}!", args);
            }
            
            main();
        """.trimIndent()

        "hid_stub" -> """
            #!/data/data/com.termux/files/usr/bin/bash
            # ${baseName} - HID payload stub generated by Termux File Manager
            
            HID_TITLE="${baseName}"
            
            banner() {
                clear
                echo "========================================"
                echo "  HID payload: ${baseName}"
                echo "========================================"
                echo
            }
            
            run_payload() {
                banner
                # TODO: put your HID keystrokes / logic here
                echo "[*] Running HID payload stub..."
            }
            
            run_payload "$@"
        """.trimIndent()

        else -> "# ${baseName} template"
    }
}

/* -----------------------------------------------------------------------
 * Permissions fixer helper
 * -------------------------------------------------------------------- */

private suspend fun fixScriptPermissionsForPath(
    storage: StorageProvider,
    logicalPath: String
): Int = withContext(Dispatchers.IO) {

    // Collect runnable scripts recursively
    suspend fun collectScripts(path: String, acc: MutableList<String>) {
        val entries = storage.list(path)
        for (item in entries) {
            if (item.isDir) {
                collectScripts(item.path, acc)
            } else if (isRunnableScript(item.path)) {
                acc.add(item.path)
            }
        }
    }

    val scripts = mutableListOf<String>()
    collectScripts(logicalPath, scripts)

    var fixed = 0

    for (logical in scripts) {
        val abs = resolveScriptAbsolutePath(storage, logical) ?: continue
        val cmd = "chmod 700 '$abs'"
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val code = p.waitFor()
            if (code == 0) fixed++
        } catch (_: Exception) {
            // ignore failures
        }
    }

    fixed
}
