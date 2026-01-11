package com.termuxfm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Very simple in-app terminal screen.
 *
 * This does NOT use any external terminal libraries – it just runs shell
 * commands via Runtime.getRuntime().exec("sh -c ...") and shows stdout/stderr.
 * It's mainly for quick commands and preview. Heavy stuff should still be done
 * in Termux proper.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    startingDir: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var currentDir by remember { mutableStateOf(startingDir.ifBlank { "/" }) }
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    fun appendOutput(text: String) {
        output = if (output.isBlank()) text else output + "\n" + text
    }

    suspend fun runShellCommand(cmd: String) {
        // Handle simple `cd` locally, no real shell session needed.
        if (cmd.startsWith("cd ")) {
            val target = cmd.removePrefix("cd").trim()
            // We don't have real filesystem checks here; just update "virtual" dir.
            currentDir = if (target.startsWith("/")) target else {
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
            appendOutput(errText.trimEnd())
        }
        appendOutput("→ exit code: $code")

        isRunning = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Terminal (preview)",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showClearConfirm = true }) {
                        Text("Clear")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Current directory: $currentDir",
                style = MaterialTheme.typography.bodySmall
            )

            // Output area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(4.dp)
            ) {
                Text(
                    text = if (output.isBlank()) "No output yet. Type a command below." else output,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            // Command input
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(if (isRunning) "Running..." else "Command") }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = !isRunning && command.isNotBlank(),
                    onClick = {
                        val cmd = command.trim()
                        command = ""
                        scope.launch {
                            runShellCommand(cmd)
                        }
                    }
                ) {
                    Text("Run")
                }

                TextButton(
                    enabled = !isRunning,
                    onClick = { command = "" }
                ) {
                    Text("Clear input")
                }
            }
        }
    }

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
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
