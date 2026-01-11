package com.termuxfm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

// -------------------------------------------------------------------
// State + ViewModel
// -------------------------------------------------------------------

data class TerminalState(
    val currentCommand: String = "",
    val output: List<String> = emptyList(),
    val isRunning: Boolean = false
)

class TerminalViewModel : ViewModel() {

    private val _state = MutableStateFlow(TerminalState())
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    // simple history
    private val history = mutableListOf<String>()
    private var historyIndex: Int = -1

    fun updateCommand(newCommand: String) {
        _state.update { it.copy(currentCommand = newCommand) }
    }

    fun navigateHistory(direction: Int) {
        if (history.isEmpty()) return

        // direction: -1 = up (older), +1 = down (newer)
        val maxIndex = history.size - 1
        historyIndex = when {
            historyIndex < 0 -> maxIndex
            else -> (historyIndex + direction).coerceIn(0, maxIndex)
        }

        val cmd = history[historyIndex]
        _state.update { it.copy(currentCommand = cmd) }
    }

    fun executeCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        // special local "clear"
        if (trimmed == "clear") {
            _state.update { it.copy(currentCommand = "", output = emptyList()) }
            return
        }

        // push to history
        history.add(trimmed)
        historyIndex = history.size

        // echo command
        appendOutput("\$ $trimmed")

        // clear input
        _state.update { it.copy(currentCommand = "", isRunning = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("sh", "-c", trimmed)
                    .redirectErrorStream(true)
                    .start()

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val lines = mutableListOf<String>()
                var line: String? = reader.readLine()
                while (line != null) {
                    lines.add(line)
                    line = reader.readLine()
                }
                val exitCode = process.waitFor()

                if (lines.isNotEmpty()) {
                    appendOutput(lines.joinToString("\n"))
                }
                appendOutput("[exit $exitCode]")
            } catch (e: Exception) {
                appendOutput("[error] ${e.message ?: "failed to execute command"}")
            } finally {
                _state.update { it.copy(isRunning = false) }
            }
        }
    }

    private fun appendOutput(text: String) {
        _state.update { st ->
            st.copy(output = st.output + text)
        }
    }
}

// -------------------------------------------------------------------
// Composable entry point to use in your app
// -------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidTerminalScreen(
    onBack: () -> Unit,
    terminalViewModel: TerminalViewModel = viewModel()
) {
    val state by terminalViewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Embedded Terminal")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { terminalViewModel.executeCommand("clear") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear output"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        AndroidTerminal(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = state,
            onCommandChange = terminalViewModel::updateCommand,
            onExecuteCommand = { terminalViewModel.executeCommand(state.currentCommand) },
            onNavigateHistory = terminalViewModel::navigateHistory
        )
    }
}

// -------------------------------------------------------------------
// Main terminal UI
// -------------------------------------------------------------------

@Composable
private fun AndroidTerminal(
    modifier: Modifier = Modifier,
    state: TerminalState,
    onCommandChange: (String) -> Unit,
    onExecuteCommand: () -> Unit,
    onNavigateHistory: (Int) -> Unit
) {
    val gradientBackground = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF161925),
            Color(0xFF101320),
            Color(0xFF080A10)
        )
    )

    Column(
        modifier = modifier
            .background(gradientBackground)
            .padding(8.dp)
    ) {

        // Output area
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF050608)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                if (state.output.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No output yet",
                            color = Color(0xFF7F8C9B),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                        Text(
                            "Run a command below (e.g. ls, pwd)",
                            color = Color(0xFF5D6B7A),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.output) { line ->
                            Text(
                                text = line,
                                color = Color(0xFFE0E3FF),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick commands row
        QuickCommandRow(
            onRun = { cmd -> onCommandChange(cmd); onExecuteCommand() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Input row
        CommandInputRow(
            command = state.currentCommand,
            onCommandChange = onCommandChange,
            onExecute = onExecuteCommand,
            onHistoryUp = { onNavigateHistory(-1) },
            onHistoryDown = { onNavigateHistory(1) }
        )
    }
}

// -------------------------------------------------------------------
// Sub-components
// -------------------------------------------------------------------

@Composable
private fun QuickCommandRow(
    onRun: (String) -> Unit
) {
    val quickCommands = listOf(
        "ls" to "List files",
        "pwd" to "Current dir",
        "whoami" to "User",
        "uname -a" to "Kernel",
        "id" to "UID/GID"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        quickCommands.forEach { (cmd, label) ->
            AssistChip(
                onClick = { onRun(cmd) },
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandInputRow(
    command: String,
    onCommandChange: (String) -> Unit,
    onExecute: () -> Unit,
    onHistoryUp: () -> Unit,
    onHistoryDown: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = command,
            onValueChange = { onCommandChange(it) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = Color(0xFFE0E3FF)
            ),
            label = { Text("Command") },
            keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(
                imeAction = ImeAction.Send
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSend = { onExecute() }
            ),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null
                )
            }
        )

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(onClick = onExecute) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Run"
            )
        }
    }

    // Simple history hints
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onHistoryUp) {
                Text("History ↑", fontSize = 11.sp)
            }
            TextButton(onClick = onHistoryDown) {
                Text("History ↓", fontSize = 11.sp)
            }
        }
    }
}
