package com.sarchiver.app.util

import java.io.File

object PathSecurity {
    /**
     * Reject archive entries that would escape [destDir] via ".." or absolute paths.
     */
    fun resolveSafe(destDir: File, entryName: String): File {
        val cleaned = entryName.replace('\\', '/').trimStart('/')
        if (cleaned.isEmpty()) error("Empty archive entry")
        cleaned.split('/').forEach { part ->
            if (part == ".." || part == ".") {
                if (part == "..") error("Path traversal blocked: $entryName")
            }
        }
        if (cleaned.startsWith("/") || cleaned.contains('\u0000')) {
            error("Illegal archive entry: $entryName")
        }
        val destCanonical = destDir.canonicalFile
        val target = File(destCanonical, cleaned).canonicalFile
        val prefix = destCanonical.path.let { if (it.endsWith(File.separator)) it else it + File.separator }
        if (target != destCanonical && !target.path.startsWith(prefix)) {
            error("Path traversal blocked: $entryName")
        }
        return target
    }

    fun isSafeArchiveName(name: String): Boolean {
        return try {
            resolveSafe(File("/tmp/sarchiver-safe"), name)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun formatBytes(n: Long): String {
        if (n < 1024) return "$n B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = n.toDouble()
        var i = -1
        while (v >= 1024 && i < units.lastIndex) {
            v /= 1024.0
            i++
        }
        return String.format("%.2f %s", v, units[i])
    }
}
