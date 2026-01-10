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
    var showNewScriptDialog by remember { mutableStateOf(false) }
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
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Text("⟳")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FloatingActionButton(onClick = { showNewScriptDialog = true }) {
                    Text("+S") // New Script
                }
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
    }

    // -------- New Plain File --------
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
                        onOpenFile(newPath)
                    } catch (e: Exception) {
                        error = e.message ?: "Failed to create file"
                    } finally {
                        showNewFileDialog = false
                    }
                }
            }
        )
    }

    // -------- New Folder --------
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

    // -------- New Script (Templates) --------
    if (showNewScriptDialog) {
        ScriptTemplateDialog(
            onDismiss = { showNewScriptDialog = false },
            onSelectTemplate = { type ->
                scope.launch {
                    try {
                        val defaultName = when (type) {
                            "bash" -> "script.sh"
                            "python" -> "script.py"
                            "node" -> "script.js"
                            "php" -> "script.php"
                            else -> "script.txt"
                        }

                        val newPath = (path.trimEnd('/') + "/$defaultName").replace("//", "/")

                        storage.createFile(newPath)

                        val template = scriptTemplateFor(type)
                        if (template.isNotEmpty()) {
                            storage.writeFile(newPath, template)
                        }

                        refresh()
                        onOpenFile(newPath)
                    } catch (e: Exception) {
                        error = e.message ?: "Failed to create script"
                    } finally {
                        showNewScriptDialog = false
                    }
                }
            }
        )
    }

    // -------- Rename --------
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

    // -------- Delete --------
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

/**
 * Dialog that lets the user choose which script template to create.
 */
@Composable
private fun ScriptTemplateDialog(
    onDismiss: () -> Unit,
    onSelectTemplate: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Script") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose a script type:")
                Button(onClick = { onSelectTemplate("bash") }) { Text("Bash (.sh)") }
                Button(onClick = { onSelectTemplate("python") }) { Text("Python (.py)") }
                Button(onClick = { onSelectTemplate("node") }) { Text("Node (.js)") }
                Button(onClick = { onSelectTemplate("php") }) { Text("PHP (.php)") }
                OutlinedButton(onClick = { onSelectTemplate("empty") }) { Text("Empty file") }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/**
 * Returns a full script template (shebang + minimal body) for the given type.
 * Relies on defaultShebangFor(...) from Editor.kt.
 */
fun scriptTemplateFor(type: String): String {
    val shebang = defaultShebangFor(type) ?: return ""
    val body = when (type) {
        "bash" -> """
            
            echo "Hello from new bash script!"
        """.trimIndent()

        "python" -> """
            
            print("Hello from new Python script!")
        """.trimIndent()

        "node" -> """
            
            console.log("Hello from new Node script!");
        """.trimIndent()

        "php" -> """
            
            <?php
            echo "Hello from new PHP script!";
            ?>
        """.trimIndent()

        else -> ""
    }
    return shebang + body
}
