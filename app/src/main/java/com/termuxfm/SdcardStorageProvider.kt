package com.termuxfm

import java.io.File

/**
 * Storage provider for non-root devices using /sdcard/TermuxProjects
 */
class SdcardStorageProvider : StorageProvider() {

    private val base = "/sdcard/TermuxProjects"

    private fun full(path: String): String =
        if (path.startsWith("/")) "$base$path"
        else "$base/$path"

    override suspend fun listFiles(path: String): List<FileItem> {
        val f = File(full(path))
        if (!f.exists() || !f.isDirectory) return emptyList()

        return f.listFiles()?.map {
            FileItem(
                name = it.name,
                path = if (path == "/") "/${it.name}" else "$path/${it.name}",
                isDirectory = it.isDirectory
            )
        } ?: emptyList()
    }

    override suspend fun readFile(path: String): String {
        return File(full(path)).readText()
    }

    override suspend fun writeFile(path: String, content: String) {
        File(full(path)).writeText(content)
    }

    override suspend fun delete(path: String) {
        File(full(path)).deleteRecursively()
    }

    override suspend fun rename(path: String, newName: String) {
        val f = File(full(path))
        val newFile = File(f.parentFile, newName)
        f.renameTo(newFile)
    }

    override suspend fun createFolder(path: String) {
        File(full(path)).mkdirs()
    }

    override suspend fun createFile(path: String) {
        val f = File(full(path))
        if (!f.exists()) {
            f.parentFile?.mkdirs()
            f.createNewFile()
        }
    }
}
