package com.termuxfm

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    storage: StorageProvider,
    filePath: String,
    onBack: () -> Unit,
    onFileSaved: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var content by rememberSaveable { mutableStateOf("") }
    var originalContent by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    var showRunDialog by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var lineCount by remember { mutableStateOf(1) }
    var hasUnsavedChanges by remember { 
        mutableStateOf(false) 
    }

    val fileName = remember(filePath) { 
        filePath.trimEnd('/').substringAfterLast("/") 
    }

    // Calculate line count when content changes
    LaunchedEffect(content) {
        lineCount = content.count { it == '\n' } + 1
        hasUnsavedChanges = content != originalContent
    }

    // Load file content
    LaunchedEffect(filePath) {
        loading = true
        status = null
        try {
            val loaded = storage.readFile(filePath)
            originalContent = loaded
            val type = detectScriptType(filePath)
            val shebang = defaultShebangFor(type)

            content = if (shebang != null) {
                when {
                    loaded.isBlank() -> "$shebang\n\n"
                    !loaded.startsWith("#!") -> "$shebang\n$loaded"
                    else -> loaded
                }
            } else {
                loaded
            }
        } catch (e: Exception) {
            status = "Read error: ${e.message}"
            content = ""
        } finally {
            loading = false
        }
    }

    // Handle back navigation with unsaved changes check
    val handleBack: () -> Unit = {
        if (hasUnsavedChanges) {
            showUnsavedChangesDialog = true
        } else {
            onBack()
        }
    }

    // Save function
    val saveFile: () -> Unit = {
        scope.launch {
            try {
                storage.writeFile(filePath, content)
                originalContent = content
                hasUnsavedChanges = false
                status = "Saved ✅"
                onFileSaved?.invoke()
                // Clear status after 2 seconds
                delay(2000)
                status = null
            } catch (e: Exception) {
                status = "Save error: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(fileName, maxLines = 1)
                        if (status != null) {
                            Text(
                                status!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Line counter
                    Text(
                        "Lines: $lineCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    
                    // Save button
                    IconButton(
                        onClick = saveFile,
                        enabled = hasUnsavedChanges
                    ) {
                        Text(
                            "Save",
                            color = if (hasUnsavedChanges) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    
                    // Run button
                    IconButton(
                        onClick = {
                            if (isRunnableScript(filePath)) {
                                showRunDialog = true
                            } else {
                                Toast.makeText(
                                    context,
                                    "Not a runnable script (.sh, .py, .bash, .js, .php)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) {
                        Text("Run")
                    }
                }
            )
        },
        floatingActionButton = {
            if (hasUnsavedChanges) {
                FloatingActionButton(
                    onClick = saveFile,
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text("SAVE", fontSize = 12.sp)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Editor with line numbers
            Row(modifier = Modifier.fillMaxSize()) {
                // Line numbers column
                Column(
                    modifier = Modifier
                        .width(40.dp)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    (1..lineCount).forEach { line ->
                        Text(
                            text = line.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 12.sp
                        )
                    }
                }

                // Divider
                Divider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Main editor
                CompositionLocalProvider(
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = MaterialTheme.colorScheme.primary,
                        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                ) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 8.dp),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        ),
                        singleLine = false,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }

    // Unsaved changes dialog
    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. What would you like to do?") },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            saveFile()
                            showUnsavedChangesDialog = false
                            onBack()
                        }
                    ) {
                        Text("Save & Exit")
                    }
                    TextButton(
                        onClick = {
                            showUnsavedChangesDialog = false
                            onBack()
                        }
                    ) {
                        Text("Exit Without Saving")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUnsavedChangesDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Run dialog
    if (showRunDialog) {
        val absPath = resolveScriptAbsolutePath(storage, filePath)
        val workDir = absPath?.substringBeforeLast('/', "/data/data/com.termux/files/home")

        if (absPath == null) {
            LaunchedEffect(showRunDialog) {
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
                title = { Text("Run Script") },
                text = { 
                    Column {
                        Text("Script: ${fileName}")
                        Text("Path: ${absPath}", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                    }
                },
                confirmButton = {
                    // Run in Termux
                    TextButton(
                        onClick = {
                            TermuxRunner.runScriptInTerminal(
                                context = context,
                                absolutePath = absPath,
                                workDir = workDir
                            )
                            showRunDialog = false
                            Toast.makeText(context, "Running in Termux...", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Run in Termux")
                    }
                },
                dismissButton = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Additional run options
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    TermuxRunner.runScriptWithSuInTerminal(
                                        context = context,
                                        absolutePath = absPath,
                                        workDir = workDir
                                    )
                                    showRunDialog = false
                                    Toast.makeText(context, "Running with su...", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Run as Root")
                            }

                            TextButton(
                                onClick = {
                                    TermuxRunner.runScriptInBackground(
                                        context = context,
                                        absolutePath = absPath,
                                        workDir = workDir
                                    )
                                    showRunDialog = false
                                    Toast.makeText(context, "Running in background...", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Background")
                            }
                        }

                        TextButton(
                            onClick = { showRunDialog = false }
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }
    }
}

internal fun isRunnableScript(path: String): Boolean {
    val lower = path.lowercase()
    return listOf(".sh", ".bash", ".py", ".js", ".php", ".rb", ".pl", ".lua")
        .any { lower.endsWith(it) }
}

internal fun resolveScriptAbsolutePath(
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

internal fun detectScriptType(path: String): String {
    val lower = path.lowercase()
    return when {
        lower.endsWith(".sh") || lower.endsWith(".bash") -> "bash"
        lower.endsWith(".py") -> "python"
        lower.endsWith(".js") -> "node"
        lower.endsWith(".php") -> "php"
        lower.endsWith(".rb") -> "ruby"
        lower.endsWith(".pl") -> "perl"
        lower.endsWith(".lua") -> "lua"
        else -> "unknown"
    }
}

internal fun defaultShebangFor(type: String): String? =
    when (type) {
        "bash" -> "#!/data/data/com.termux/files/usr/bin/bash"
        "python" -> "#!/data/data/com.termux/files/usr/bin/python"
        "node" -> "#!/data/data/com.termux/files/usr/bin/node"
        "php" -> "#!/data/data/com.termux/files/usr/bin/php"
        "ruby" -> "#!/data/data/com.termux/files/usr/bin/ruby"
        "perl" -> "#!/data/data/com.termux/files/usr/bin/perl"
        "lua" -> "#!/data/data/com.termux/files/usr/bin/lua"
        else -> null
    }
