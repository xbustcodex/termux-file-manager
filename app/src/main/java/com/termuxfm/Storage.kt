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
 * Legacy /sdcard provider (non-root fallback).
 */
class LegacyFileStorageProvider(private val rootPath: String) : StorageProvider {

    override fun isReady(): Boolean = File(rootPath).exists()

    private fun resolve(path: String): File {
        val clean = path.trimStart('/')
        return if (clean.isBlank()) File(rootPath) else File(rootPath, clean)
    }

    // --- Permission helpers -------------------------------------------------

    private fun tryFixFilePermissions(file: File): Boolean {
        if (!file.exists()) return false

        return try {
            val cmd = "chmod 664 '${file.absolutePath}'"
            val process = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val code = process.waitFor()
            code == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun tryFixDirPermissions(dir: File): Boolean {
        if (!dir.exists()) return false

        return try {
            val cmd = "chmod 775 '${dir.absolutePath}'"
            val process = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val code = process.waitFor()
            code == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun ensureReadable(file: File): Boolean {
        if (file.canRead()) return true
        if (tryFixFilePermissions(file)) {
            return file.canRead()
        }
        return false
    }

    private fun ensureWritable(file: File): Boolean {
        if (file.canWrite()) return true
        if (tryFixFilePermissions(file)) {
            return file.canWrite()
        }
        return false
    }

    private fun ensureDirWritable(dir: File): Boolean {
        if (dir.canWrite()) return true
        if (tryFixDirPermissions(dir)) {
            return dir.canWrite()
        }
        return false
    }

    // --- StorageProvider implementation -------------------------------------

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = resolve(path)

        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()

        if (!dir.canRead()) {
            if (!ensureReadable(dir)) {
                return@withContext emptyList()
            }
        }

        val files = dir.listFiles()?.map {
            FileItem(
                name = it.name,
                path = (path.trimEnd('/') + "/" + it.name).replace("//", "/"),
                isDir = it.isDirectory,
                size = if (it.isFile) it.length() else null,
                modified = it.lastModified()
            )
        } ?: emptyList()

        files.sortedWith(
            compareBy<FileItem> { !it.isDir }.thenBy { it.name.lowercase() }
        )
    }

    override suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
        val file = resolve(path)

        if (!file.exists() || !file.isFile) return@withContext ""

        if (file.canRead()) {
            return@withContext file.readText()
        }

        if (ensureReadable(file)) {
            return@withContext file.readText()
        }

        ""
    }

    override suspend fun writeFile(path: String, content: String) = withContext(Dispatchers.IO) {
        val file = resolve(path)
        val parent = file.parentFile

        if (parent != null && parent.exists() && !ensureDirWritable(parent)) {
            error("Write failed: parent directory not writable")
        }

        if (!file.exists()) {
            file.createNewFile()
        }

        if (!ensureWritable(file)) {
            error("Write failed: file not writable")
        }

        file.writeText(content)
    }

    override suspend fun createFile(path: String) {
        withContext(Dispatchers.IO) {
            val file = resolve(path)
            val parent = file.parentFile

            if (parent != null) {
                if (!parent.exists() && !parent.mkdirs()) {
                    error("Create file failed: could not create directory")
                }

                if (!ensureDirWritable(parent)) {
                    error("Create file failed: directory not writable")
                }
            }

            if (!file.exists()) {
                if (!file.createNewFile()) {
                    error("Create file failed")
                }
            }

            // Make sure it’s writable afterwards
            ensureWritable(file)
        }
    }

    override suspend fun createFolder(path: String) {
        withContext(Dispatchers.IO) {
            val dir = resolve(path)
            val parent = dir.parentFile

            if (parent != null && parent.exists() && !ensureDirWritable(parent)) {
                error("Create folder failed: parent directory not writable")
            }

            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    error("Create folder failed")
                }
            }

            ensureDirWritable(dir)
        }
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val f = resolve(path)

        fun deleteRecursively(file: File): Boolean {
            if (file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    if (!deleteRecursively(child)) return false
                }
            }
            if (!file.canWrite()) {
                ensureWritable(file)
            }
            return file.delete()
        }

        if (!deleteRecursively(f)) {
            error("Delete failed")
        }
    }

    override suspend fun rename(path: String, newName: String) = withContext(Dispatchers.IO) {
        val f = resolve(path)
        val parent = f.parentFile ?: error("Rename failed: no parent directory")

        if (!ensureDirWritable(parent)) {
            error("Rename failed: parent directory not writable")
        }

        val target = File(parent, newName)
        if (!f.renameTo(target)) error("Rename failed")
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

    private fun findDoc(path: String): DocumentFile? {
        val root = rootDoc() ?: return null
        val clean = path.trim()

        if (clean.isEmpty() || clean == "/") return root

        val segments = clean.trimStart('/').split('/')
        var current: DocumentFile? = root
        for (seg in segments) {
            current = current?.findFile(seg)
            if (current == null) break
        }
        return current
    }

    override fun isReady(): Boolean = rootDoc() != null

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val doc = findDoc(path) ?: return@withContext emptyList()
        val children = doc.listFiles().map {
            FileItem(
                name = it.name.orEmpty(),
                path = (path.trimEnd('/') + "/" + (it.name ?: "")).replace("//", "/"),
                isDir = it.isDirectory,
                size = if (it.isFile) it.length() else null,
                modified = it.lastModified()
            )
        }

        children.sortedWith(compareBy<FileItem> { !it.isDir }.thenBy { it.name.lowercase() })
    }

    override suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
        val doc = findDoc(path) ?: return@withContext ""
        val input = context.contentResolver.openInputStream(doc.uri) ?: return@withContext ""
        input.bufferedReader().use { it.readText() }
    }

    override suspend fun writeFile(path: String, content: String) = withContext(Dispatchers.IO) {
        val doc = findDoc(path) ?: error("File not found")
        val output = context.contentResolver.openOutputStream(doc.uri, "rwt")
            ?: error("Cannot open output stream")
        output.bufferedWriter().use { it.write(content) }
    }

    override suspend fun createFolder(path: String) {
        withContext(Dispatchers.IO) {
            val parentPath = path.trimEnd('/').substringBeforeLast("/", "")
            val name = path.trimEnd('/').substringAfterLast("/")
            val parent = findDoc(parentPath) ?: error("Parent not found")
            parent.createDirectory(name) ?: error("Failed to create folder")
        }
    }

    override suspend fun createFile(path: String) {
        withContext(Dispatchers.IO) {
            val parentPath = path.trimEnd('/').substringBeforeLast("/", "")
            val name = path.trimEnd('/').substringAfterLast("/")
            val parent = findDoc(parentPath) ?: error("Parent not found")
            parent.createFile("text/plain", name) ?: error("Failed to create file")
        }
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val doc = findDoc(path) ?: return@withContext
        if (!doc.delete()) error("Delete failed")
    }

    override suspend fun rename(path: String, newName: String) = withContext(Dispatchers.IO) {
        val doc = findDoc(path) ?: error("Not found")
        if (!doc.renameTo(newName)) error("Rename failed")
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
