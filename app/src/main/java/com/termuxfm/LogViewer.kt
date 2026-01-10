package com.termuxfm

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val MAX_LOG_CHARS = 128 * 1024 // 128 KB

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    storage: StorageProvider,
    filePath: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var rawText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    val fileName = remember(filePath) {
        filePath.trimEnd('/').substringAfterLast("/")
    }

    // Load file contents
    LaunchedEffect(filePath) {
        loading = true
        status = null
        try {
            val data = storage.readFile(filePath)
            if (data.length > MAX_LOG_CHARS) {
                rawText = data.substring(0, MAX_LOG_CHARS)
                status = "Showing first ${MAX_LOG_CHARS / 1024} KB (truncated)"
            } else {
                rawText = data
            }
        } catch (e: Exception) {
            rawText = ""
            status = "Read error: ${e.message}"
            Toast.makeText(context, "Failed to read log file", Toast.LENGTH_LONG).show()
        } finally {
            loading = false
        }
    }

    // Split into lines and filter by query
    val allLines = remember(rawText) { rawText.lines() }
    val filteredLines = remember(rawText, query) {
        if (query.isBlank()) {
            allLines
        } else {
            val q = query.lowercase()
            allLines.filter { it.lowercase().contains(q) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log: $fileName") },
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status / info
            if (status != null) {
                Text(
                    status!!,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Search bar
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search in log") },
                singleLine = true
            )

            val visibleCount = filteredLines.size
            val totalCount = allLines.size
            Text(
                text = if (query.isBlank()) {
                    "Lines: $totalCount"
                } else {
                    "Matches: $visibleCount / $totalCount lines"
                },
                style = MaterialTheme.typography.bodySmall
            )

            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Log text
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    filteredLines.forEachIndexed { index, line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }
        }
    }
}
