package com.termuxfm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    startingDir: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var currentDir by remember { mutableStateOf(startingDir.ifBlank { "/" }) }
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // simple history list – newest first
    var history by remember { mutableStateOf(listOf<String>()) }

    val scrollState = rememberScrollState()

    // Append output (with crude error marker)
    fun appendOutput(text: String, isError: Boolean = false) {
        val line = if (isError) "❌ $text" else text
        output = if (output.isBlank()) line else output + "\n" + line
    }

    suspend fun runShellCommand(cmd: String) {
        // Handle simple `cd` locally (virtual)
        if (cmd.startsWith("cd ")) {
            val target = cmd.removePrefix("cd").trim()
            currentDir = if (target.startsWith("/")) {
                target
            } else {
                val base = currentDir.trimEnd('/')
                (if (base.isBlank()) "/" else base) + "/" + target
            }
            appendOutput("$currentDir \$ $cmd")
            appendOutput("→ (virtual) directory changed to $currentDir")
            return
        }

        isRunning = true
        appendOutput("$currentDir \$ $cmd")

        val result = withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                val stdout = BufferedReader(InputStreamReader(process.inputStream))
                val stderr = BufferedReader(InputStreamReader(process.errorStream))

                val outText = stdout.readText()
                val errText = stderr.readText()

                val code = process.waitFor()
                Triple(outText, errText, code)
            } catch (e: Exception) {
                Triple("", "Error: ${e.message}", -1)
            }
        }

        val (outText, errText, code) = result

        if (outText.isNotBlank()) {
            appendOutput(outText.trimEnd())
        }
        if (errText.isNotBlank()) {
            appendOutput(errText.trimEnd(), isError = true)
        }
        appendOutput("→ exit code: $code")

        // update history (no duplicate at top, cap 20)
        if (cmd.isNotBlank() && history.firstOrNull() != cmd) {
            history = listOf(cmd) + history.take(19)
        }

        isRunning = false
    }

    // Auto-scroll on new output
    LaunchedEffect(output) {
        if (output.isNotBlank()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Dragon-theme colors
    val bgDark = Color(0xFF05060A)
    val panelDark = Color(0xFF111319)
    val neonGreen = Color(0xFF00FF7F)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Terminal",
                            style = MaterialTheme.typography.titleMedium,
                            color = neonGreen
                        )
                        Text(
                            currentDir,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back", color = neonGreen)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showClearConfirm = true },
                        enabled = output.isNotBlank()
                    ) {
                        Text("Clear", color = neonGreen)
                    }
                    TextButton(
                        onClick = {
                            if (output.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(output))
                                appendOutput("→ Output copied to clipboard")
                            }
                        },
                        enabled = output.isNotBlank()
                    ) {
                        Text("Copy", color = neonGreen)
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .background(bgDark)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Output panel
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = panelDark
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = if (output.isBlank())
                            "No output yet.\nType a command below and press Run."
                        else output,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = neonGreen
                        )
                    )
                }
            }

            // Command input + controls
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(
                            if (isRunning) "Running…" else "Command",
                            color = neonGreen
                        )
                    },
                    placeholder = { Text("ls, pwd, cd .., etc.") }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        enabled = !isRunning && command.isNotBlank(),
                        onClick = {
                            val cmd = command.trim()
                            if (cmd.isNotBlank()) {
                                command = ""
                                scope.launch {
                                    runShellCommand(cmd)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = neonGreen,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(if (isRunning) "Working…" else "Run")
                    }

                    TextButton(
                        enabled = !isRunning && command.isNotBlank(),
                        onClick = { command = "" }
                    ) {
                        Text("Clear input", color = neonGreen)
                    }
                }

                // Quick history chips (top 3)
                if (history.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Recent commands",
                            style = MaterialTheme.typography.labelSmall,
                            color = neonGreen
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            history.take(3).forEach { cmd ->
                                TextButton(
                                    onClick = { command = cmd },
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline,
                                            MaterialTheme.shapes.small
                                        )
                                ) {
                                    Text(
                                        cmd,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        color = neonGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Clear output dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear output") },
            text = { Text("Clear all terminal output from this session?") },
            confirmButton = {
                TextButton(onClick = {
                    output = ""
                    showClearConfirm = false
                }) {
                    Text("Clear", color = neonGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = neonGreen)
                }
            }
        )
    }
}
