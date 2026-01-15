package com.termuxfm

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexViewerScreen(
    storage: StorageProvider,
    filePath: String,
    onBack: () -> Unit
) {
    var hexDump by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filePath, storage) {
        loading = true
        error = null
        try {
            // 🔑 Use StorageProvider, same as editor/log viewer
            val text = storage.readFile(filePath)
            val bytes = text.toByteArray()   // good enough for scripts / text files
            hexDump = buildHexDump(bytes)
        } catch (e: Exception) {
            error = "Error reading file: $filePath: ${e.message}"
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hex: ${filePath.substringAfterLast('/')}") },
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
            when {
                loading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                error != null -> {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                else -> {
                    Text(
                        text = hexDump,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        }
    }
}

/**
 * Simple hex dump: offset | hex bytes | ASCII
 */
private fun buildHexDump(
    bytes: ByteArray,
    bytesPerRow: Int = 16
): String {
    if (bytes.isEmpty()) return "<empty file>"

    val sb = StringBuilder()
    var offset = 0

    while (offset < bytes.size) {
        val rowEnd = min(offset + bytesPerRow, bytes.size)
        val row = bytes.sliceArray(offset until rowEnd)

        // Offset
        sb.append(String.format("%08X  ", offset))

        // Hex bytes
        for (i in 0 until bytesPerRow) {
            if (offset + i < bytes.size) {
                sb.append(String.format("%02X ", bytes[offset + i]))
            } else {
                sb.append("   ")
            }
            if (i == 7) sb.append(" ")
        }

        sb.append(" ")

        // ASCII representation
        for (b in row) {
            val c = b.toInt() and 0xFF
            val ch = if (c in 0x20..0x7E) c.toChar() else '.'
            sb.append(ch)
        }

        sb.append('\n')
        offset += bytesPerRow
    }

    return sb.toString()
}
