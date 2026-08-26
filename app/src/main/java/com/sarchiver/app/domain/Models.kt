package com.sarchiver.app.domain

enum class StorageKind { INTERNAL, SAF, OTG, MTP, ARCHIVE }

data class FsNode(
    val id: String,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val mime: String?,
    val kind: StorageKind,
    val canWrite: Boolean,
    val extra: String? = null,
)

data class TransferProgress(
    val jobId: String,
    val title: String,
    val currentName: String,
    val bytesDone: Long,
    val bytesTotal: Long,
    val filesDone: Int,
    val filesTotal: Int,
    val bytesPerSecond: Long,
    val paused: Boolean,
    val cancelled: Boolean,
    val error: String?,
    val finished: Boolean,
) {
    val etaSeconds: Long
        get() = if (bytesPerSecond <= 0L || bytesDone >= bytesTotal) 0L
        else ((bytesTotal - bytesDone) / bytesPerSecond)
}

enum class SortMode { NAME, SIZE, DATE, TYPE }
enum class ViewMode { LIST, GRID }

data class ArchiveEntryInfo(
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
    val lastModified: Long,
)
