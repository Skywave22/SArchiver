package com.sarchiver.app.util

import com.sarchiver.app.domain.FsNode
import com.sarchiver.app.domain.SortMode

object FileSort {
    fun sort(items: List<FsNode>, mode: SortMode, ascending: Boolean = true): List<FsNode> {
        val dirsFirst = compareByDescending<FsNode> { it.isDirectory }
        val cmp = when (mode) {
            SortMode.NAME -> dirsFirst.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortMode.SIZE -> dirsFirst.thenBy { it.size }
            SortMode.DATE -> dirsFirst.thenBy { it.lastModified }
            SortMode.TYPE -> dirsFirst.thenBy { extension(it.name) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        val sorted = items.sortedWith(cmp)
        return if (ascending) sorted else {
            val dirs = sorted.filter { it.isDirectory }.reversed()
            val files = sorted.filter { !it.isDirectory }.reversed()
            dirs + files
        }
    }

    fun filter(items: List<FsNode>, query: String, extension: String?): List<FsNode> {
        val q = query.trim()
        return items.filter { node ->
            val nameOk = q.isEmpty() || node.name.contains(q, ignoreCase = true)
            val extOk = extension.isNullOrBlank() ||
                node.isDirectory ||
                node.name.endsWith(".$extension", ignoreCase = true)
            nameOk && extOk
        }
    }

    fun extension(name: String): String {
        val i = name.lastIndexOf('.')
        return if (i <= 0) "" else name.substring(i + 1).lowercase()
    }
}
