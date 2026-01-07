package com.termuxfm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Storage provider for non-root devices using /sdcard/TermuxProjects
 */
class SdcardStorageProvider : StorageProvider() {

    private val baseDir = File("/sdcard/TermuxProjects")

    private fun resolve(path: String): File {
        return if (path.isBlank() || path == "/") {
            baseDir
        } else {
            File(baseDir, path.removePrefix("/"))
        }
    }

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = resolve(path)
        if (!dir.exists() || !dir.isDirectory) {
            return@withContext emptyList<FileItem>()
        }

        val files = dir.listFiles() ?: return@withContext emptyList<FileItem>()

        files
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .map { f ->
                FileItem(
                    name = f.name,
                    path = if (path == "/" || path.isBlank()) "/${f.name}" else "$path/${f.name}",
                    isDirectory = f.isDirectory
                )
            }
    }

    override suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
        val file = resolve(path)
        file.readText()
    }

    override suspend fun writeFile(path: String, content: String) = withContext(Dispatchers.IO) {
        val file = resolve(path)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val file = resolve(path)
        if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    override suspend fun rename(path: String, newName: String) = withContext(Dispatchers.IO) {
        val file = resolve(path)
        val parent = file.parentFile ?: return@withContext
        val target = File(parent, newName)
        file.renameTo(target)
    }

    override suspend fun createFolder(path: String) = withContext(Dispatchers.IO) {
        val dir = resolve(path)
        dir.mkdirs()
    }

    override suspend fun createFile(path: String) = withContext(Dispatchers.IO) {
        val file = resolve(path)
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.createNewFile()
        }
    }
}

