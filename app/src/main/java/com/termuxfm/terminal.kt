package com.termuxfm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TerminalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startPath = intent.getStringExtra("path") ?: "/"

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF050509)
                ) {
                    TerminalScreen(startPath = startPath)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(startPath: String) {
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var workingDir by remember { mutableStateOf(File(startPath)) }
    var command by remember { mutableStateOf("") }
    var outputLines by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var runAsRoot by remember { mutableStateOf(false) }

    // History (newest first)
    val history = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableStateOf(-1) }

    // Simple autocomplete suggestions
    val suggestions = listOf(
        "ls",
        "ls -la",
        "pwd",
        "cd ..",
        "mkdir ",
        "rm ",
        "cat ",
        "chmod +x "
    )

    val listState = rememberLazyListState()

    fun appendOutput(text: String) {
        if (text.isEmpty()) return
        outputLines = outputLines + text.lines()
    }

    fun runCommand(rawCommand: String) {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty() || isRunning) return

        history.add(0, trimmed)
        historyIndex = -1
        command = ""

        appendOutput("$ $trimmed")

        coroutineScope.launch(Dispatchers.IO) {
            isRunning = true
            try {
                val fullCommand = if (runAsRoot) {
                    // Termux usually executes root commands via su -c
                    "su -c \"$trimmed\""
                } else {
                    trimmed
                }

                val pb = ProcessBuilder("sh", "-c", fullCommand)
                    .directory(workingDir)
                    .redirectErrorStream(true)

                val process = pb.start()
                val textOut = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                withContext(Dispatchers.Main) {
                    if (textOut.isNotBlank()) {
                        appendOutput(textOut.trimEnd())
                    }
                    appendOutput("[exit code $exitCode]")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    appendOutput("Error: ${t.message}")
                }
            } finally {
                isRunning = false
            }
        }
    }

    // Auto-scroll output when new lines appear
    LaunchedEffect(outputLines.size) {
        if (outputLines.isNotEmpty()) {
            listState.animateScrollToItem(outputLines.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF050509),
                        Color(0xFF050509),
                        Color(0xFF080012)
                    )
                )
            )
            .padding(12.dp)
    ) {
        // Header
        Text(
            text = "Termux Terminal",
            color = Color(0xFFB0F4FF),
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Dir: ${workingDir.absolutePath}",
            color = Color(0xFF8A8AFF),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Output window
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color(0xFF40406A),
                    shape = RoundedCornerShape(16.dp)
                )
                .background(Color(0xFF050509), RoundedCornerShape(16.dp))
                .padding(10.dp)
        ) {
            if (outputLines.isEmpty()) {
                Text(
                    text = "Command output will appear here…",
                    color = Color(0xFF555566),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(outputLines) { line ->
                        val isError = line.contains("error", ignoreCase = true) ||
                                line.contains("denied", ignoreCase = true) ||
                                line.contains("not found", ignoreCase = true)

                        Text(
                            text = line,
                            color = if (isError) Color(0xFFFF6B6B) else Color(0xFFE5E5F5),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Suggestions row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            suggestions.forEach { s ->
                SuggestionChip(
                    onClick = {
                        command = if (command.isBlank()) s else "$command $s"
                    },
                    label = {
                        Text(
                            text = s, 
                            fontSize = 11.sp,
                            color = Color(0xFFD9D9FF)
                        )
                    },
                    colors = ChipDefaults.suggestionChipColors(
                        containerColor = Color(0xFF141427),
                        labelColor = Color(0xFFD9D9FF)
                    ),
                    border = ChipDefaults.suggestionChipBorder(
                        borderColor = Color(0xFF40406A),
                        borderWidth = 1.dp
                    )
                )
            }
        }

        // Root toggle + copy output
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = runAsRoot,
                    onCheckedChange = { runAsRoot = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF050509),
                        checkedTrackColor = Color(0xFF00F5A0),
                        uncheckedThumbColor = Color(0xFF8888AA),
                        uncheckedTrackColor = Color(0xFF303048)
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (runAsRoot) "Run as root" else "Run as normal user",
                    color = Color(0xFFCCCCF0),
                    fontSize = 12.sp
                )
            }

            TextButton(
                onClick = {
                    val joined = outputLines.joinToString("\n")
                    clipboardManager.setText(AnnotatedString(joined))
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy output",
                    tint = Color(0xFFB0F4FF)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Copy output",
                    color = Color(0xFFB0F4FF),
                    fontSize = 12.sp
                )
            }
        }

        // Command input + history controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                maxLines = 4, // multi-line command support
                label = {
                    Text(
                        text = "Command",
                        color = Color(0xFF84FFB8)
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color.White
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        runCommand(command)
                        focusManager.clearFocus()
                    }
                ),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    cursorColor = Color(0xFF84FFB8),
                    focusedBorderColor = Color(0xFF84FFB8),
                    unfocusedBorderColor = Color(0xFF9B5BFF),
                    containerColor = Color(0xFF101018)
                ),
                trailingIcon = {
                    Row {
                        IconButton(
                            onClick = {
                                // history up (older)
                                if (history.isNotEmpty()) {
                                    val nextIndex = (if (historyIndex == -1) 0 else historyIndex + 1)
                                        .coerceAtMost(history.lastIndex)
                                    historyIndex = nextIndex
                                    command = history[historyIndex]
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Previous command",
                                tint = Color(0xFFB0F4FF)
                            )
                        }
                        IconButton(
                            onClick = {
                                // history down (newer)
                                if (history.isNotEmpty() && historyIndex > 0) {
                                    val nextIndex = (historyIndex - 1).coerceAtLeast(0)
                                    historyIndex = nextIndex
                                    command = history[historyIndex]
                                } else if (historyIndex == 0) {
                                    historyIndex = -1
                                    command = ""
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Next command",
                                tint = Color(0xFFB0F4FF)
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    runCommand(command)
                    focusManager.clearFocus()
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00F5A0),
                                Color(0xFF9B5BFF)
                            )
                        )
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Run command",
                    tint = Color(0xFF050509)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    colors: ChipColors = ChipDefaults.suggestionChipColors(),
    border: ChipBorder? = null,
    enabled: Boolean = true
) {
    androidx.compose.material3.AssistChip(
        onClick = onClick,
        label = label,
        modifier = modifier,
        colors = colors,
        border = border,
        enabled = enabled
    )
}
