package com.sarchiver.app.data.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.documentfile.provider.DocumentFile
import com.sarchiver.app.domain.FsNode
import com.sarchiver.app.domain.StorageKind
import java.io.File

class StorageRepository(private val context: Context) {

    fun roots(): List<FsNode> {
        val list = mutableListOf<FsNode>()
        val internal = Environment.getExternalStorageDirectory()
        if (internal != null) {
            list += fileToNode(internal, StorageKind.INTERNAL, "Internal storage")
        }
        context.getExternalFilesDir(null)?.let {
            list += fileToNode(it, StorageKind.INTERNAL, "App files")
        }
        val sm = context.getSystemService(StorageManager::class.java)
        sm?.storageVolumes?.forEach { vol ->
            val dir: File? = if (Build.VERSION.SDK_INT >= 30) vol.directory else {
                @Suppress("DEPRECATION")
                vol.javaClass.getMethod("getPathFile").invoke(vol) as? File
            }
            if (dir != null && dir.exists()) {
                val kind = if (vol.isRemovable) StorageKind.OTG else StorageKind.INTERNAL
                val label = vol.getDescription(context)
                if (list.none { it.path == dir.absolutePath }) {
                    list += fileToNode(dir, kind, label).copy(canWrite = vol.state == Environment.MEDIA_MOUNTED)
                }
            }
        }
        return list
    }

    fun list(path: String): List<FsNode> {
        val dir = File(path)
        if (!dir.isDirectory) return emptyList()
        val files = dir.listFiles() ?: return emptyList()
        return files.map { fileToNode(it, kindFor(it), it.name) }
            .sortedWith(compareByDescending<FsNode> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    fun createFolder(parent: String, name: String): File {
        val f = File(parent, name)
        if (!f.mkdirs() && !f.isDirectory) error("Could not create folder")
        return f
    }

    fun createFile(parent: String, name: String): File {
        val f = File(parent, name)
        if (!f.createNewFile() && !f.exists()) error("Could not create file")
        return f
    }

    fun rename(path: String, newName: String): File {
        val src = File(path)
        val dest = File(src.parentFile, newName)
        if (!src.renameTo(dest)) error("Rename failed")
        return dest
    }

    fun delete(path: String): Boolean {
        val f = File(path)
        return if (f.isDirectory) f.deleteRecursively() else f.delete()
    }

    fun folderSize(path: String): Long {
        val f = File(path)
        if (!f.exists()) return 0
        if (f.isFile) return f.length()
        var sum = 0L
        f.walkTopDown().forEach { if (it.isFile) sum += it.length() }
        return sum
    }

    fun stat(path: String): Pair<Long, Long> {
        return try {
            val s = StatFs(path)
            s.availableBytes to s.totalBytes
        } catch (_: Exception) {
            0L to 0L
        }
    }

    fun listSaf(uri: Uri): List<FsNode> {
        val doc = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()
        return doc.listFiles().map {
            FsNode(
                id = it.uri.toString(),
                name = it.name ?: "unnamed",
                path = it.uri.toString(),
                isDirectory = it.isDirectory,
                size = it.length(),
                lastModified = it.lastModified(),
                mime = it.type,
                kind = StorageKind.SAF,
                canWrite = it.canWrite(),
            )
        }
    }

    private fun kindFor(f: File): StorageKind {
        val p = f.absolutePath
        return if (p.startsWith("/mnt/media_rw") || p.contains("/usb")) StorageKind.OTG else StorageKind.INTERNAL
    }

    private fun fileToNode(f: File, kind: StorageKind, display: String? = null) = FsNode(
        id = f.absolutePath,
        name = display ?: f.name,
        path = f.absolutePath,
        isDirectory = f.isDirectory,
        size = if (f.isFile) f.length() else 0L,
        lastModified = f.lastModified(),
        mime = null,
        kind = kind,
        canWrite = f.canWrite(),
    )
}
