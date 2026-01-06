package com.termuxfm

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    storage: StorageProvider,
    filePath: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }

    val fileName = remember(filePath) { filePath.trimEnd('/').substringAfterLast("/") }

    LaunchedEffect(filePath) {
        loading = true
        status = null
        try {
            content = storage.readFile(filePath)
        } catch (e: Exception) {
            status = "Read error: ${e.message}"
            content = ""
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                try {
                                    storage.writeFile(filePath, content)
                                    status = "Saved ✅"
                                } catch (e: Exception) {
                                    status = "Save error: ${e.message}"
                                }
                            }
                        }
                    ) { Text("Save") }

                    TextButton(
                        onClick = {
                            // Placeholder: we'll hook Termux runner next
                            status = "Run: coming next (Termux integration)"
                        }
                    ) { Text("Run") }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (status != null) {
                Text(status!!)
            }

            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxSize(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                singleLine = false
            )
        }
    }
}

