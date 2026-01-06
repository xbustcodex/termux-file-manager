package com.termuxfm

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class FileItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long? = null,
    val modified: Long? = null
)

interface StorageProvider {
    suspend fun list(path: String): List<FileItem>
    suspend fun readFile(path: String): String
    suspend fun writeFile(path: String, content: String)
    suspend fun createFolder(path: String)
    suspend fun createFile(path: String)
    suspend fun delete(path: String)
    suspend fun rename(path: String, newName: String)
    fun isReady(): Boolean
}

enum class WorkspaceMode { SAF, LEGACY_SD }

data class WorkspaceConfig(
    val mode: WorkspaceMode,
    val safTreeUri: Uri? = null,
    val legacyRootPath: String = "/sdcard/TermuxProjects"
)

object RootCheck {
    fun hasRoot(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }
}

fun chooseWorkspaceConfig(context: Context): WorkspaceConfig {
    val prefs = context.getSharedPreferences("termuxfm", Context.MODE_PRIVATE)
    val rooted = RootCheck.hasRoot()

    return if (!rooted) {
        WorkspaceConfig(mode = WorkspaceMode.LEGACY_SD)
    } else {
        val saved = prefs.getString("saf_tree_uri", null)
        val uri = saved?.let { Uri.parse(it) }
        WorkspaceConfig(mode = WorkspaceMode.SAF, safTreeUri = uri)
    }
}

fun ensureLegacyWorkspaceExists(rootPath: String = "/sdcard/TermuxProjects") {
    val dir = File(rootPath)
    if (!dir.exists()) dir.mkdirs()
}

/**
 * LEGACY provider using normal filesystem (for /sdcard/TermuxProjects).
 */
class LegacyFileStorageProvider(private val rootPath: String) : StorageProvider {

    override fun isReady(): Boolean = File(rootPath).exists()

    private fun resolve(path: String): File {
        val clean = path.trimStart('/')
        return if (clean.isBlank()) File(rootPath) else File(rootPath, clean)
    }

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = resolve(path)
        val files = dir.listFiles()?.map {
            FileItem(
                name = it.name,
                path = (path.trimEnd('/') + "/" + it.name).replace("//", "/"),
                isDir = it.isDirectory,
                size = if (it.isFile) it.length() else null,
                modified = it.lastModified()
            )
        } ?: emptyList()

        files.sortedWith(compareBy<FileItem> { !it.isDir }.thenBy { it.name.lowercase() })
    }

    override suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
        resolve(path).takeIf { it.exists() }?.readText() ?: ""
    }

    override suspend fun writeFile(path: String, content: String) = withContext(Dispatchers.IO) {
        val f = resolve(path)
        f.parentFile?.mkdirs()
        f.writeText(content)
    }

    override suspend fun createFolder(path: String) = withContext(Dispatchers.IO) {
        resolve(path).mkdirs()
        Unit
    }

    override suspend fun createFile(path: String) = withContext(Dispatchers.IO) {
        val f = resolve(path)
        f.parentFile?.mkdirs()
        if (!f.exists()) f.createNewFile()
        Unit
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        resolve(path).deleteRecursively()
        Unit
    }

    override suspend fun rename(path: String, newName: String) = withContext(Dispatchers.IO) {
        val f = resolve(path)
        val target = File(f.parentFile, newName)
        if (!f.renameTo(target)) error("Rename failed")
        Unit
    }
}

/**
 * SAF provider (DocumentFile tree).
 */
class SafStorageProvider(
    private val context: Context,
    private val treeUri: Uri?
) : StorageProvider {

    private fun rootDoc(): DocumentFile? =
        treeUri?.let { DocumentFile.fromTreeUri(context, it) }

    override fun isReady(): Boolean = rootDoc() != null

    private fun findDoc(path: String): DocumentFile? {
        val root = rootDoc() ?: return null
        if (path == "/" || path.isBlank()) return root

        var current = root
        val parts = path.trim('/').split("/").filter { it.isNotBlank() }
        for (p in parts) {
            current = current.findFile(p) ?: return null
        }
        return current
    }

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = findDoc(path) ?: return@withContext emptyList()
        dir.listFiles().map {
            FileItem(
                name = it.name ?: "unknown",
                path = (path.trimEnd('/') + "/" + (it.name ?: "")).replace("//", "/"),
                isDir = it.isDirectory,
                size = if (it.isFile) it.length() else null,
                modified = it.lastModified()
            )
        }.sortedWith(compareBy<FileItem> { !it.isDir }.thenBy { it.name.lowercase() })
    }

    override suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
        val file = findDoc(path) ?: error("File not found: $path")
        context.contentResolver.openInputStream(file.uri)?.use { ins ->
            ins.bufferedReader().readText()
        } ?: ""
    }

    override suspend fun writeFile(path: String, content: String) = withContext(Dispatchers.IO) {
        val file = findDoc(path) ?: error("File not found: $path")
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
            out.bufferedWriter().use { it.write(content) }
        }
        Unit
    }

    override suspend fun createFolder(path: String) = withContext(Dispatchers.IO) {
        val parentPath = path.trimEnd('/').substringBeforeLast("/", "/")
        val name = path.trimEnd('/').substringAfterLast("/")
        val parent = findDoc(parentPath) ?: error("Parent not found: $parentPath")
        parent.createDirectory(name) ?: error("Failed to create folder")
        Unit
    }

    override suspend fun createFile(path: String) = withContext(Dispatchers.IO) {
        val parentPath = path.substringBeforeLast("/", "/")
        val name = path.substringAfterLast("/")
        val parent = findDoc(parentPath) ?: error("Parent not found: $parentPath")
        parent.createFile("text/plain", name) ?: error("Failed to create file")
        Unit
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val doc = findDoc(path) ?: return@withContext Unit
        if (!doc.delete()) error("Delete failed")
        Unit
    }

    override suspend fun rename(path: String, newName: String) = withContext(Dispatchers.IO) {
        val doc = findDoc(path) ?: error("Not found")
        if (!doc.renameTo(newName)) error("Rename failed")
        Unit
    }
}

/**
 * Helper: persist SAF permission
 */
fun persistTreePermission(context: Context, uri: Uri) {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    context.contentResolver.takePersistableUriPermission(uri, flags)
    val prefs = context.getSharedPreferences("termuxfm", Context.MODE_PRIVATE)
    prefs.edit().putString("saf_tree_uri", uri.toString()).apply()
}

