package com.sarchiver.app.data.transfer

import com.sarchiver.app.domain.TransferProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class TransferEngine {
    private val jobs = ConcurrentHashMap<String, MutableStateFlow<TransferProgress>>()
    val active: Map<String, StateFlow<TransferProgress>> get() = jobs

    fun newJob(title: String, filesTotal: Int, bytesTotal: Long): String {
        val id = UUID.randomUUID().toString()
        jobs[id] = MutableStateFlow(
            TransferProgress(id, title, "", 0, bytesTotal, 0, filesTotal, 0, false, false, null, false)
        )
        return id
    }

    fun cancel(id: String) {
        jobs[id]?.let { it.value = it.value.copy(cancelled = true) }
    }

    suspend fun copyFile(
        jobId: String,
        name: String,
        input: () -> InputStream,
        output: () -> OutputStream,
        size: Long,
    ) = withContext(Dispatchers.IO) {
        val flow = jobs[jobId] ?: return@withContext
        val bufSize = bufferFor(size)
        val buf = ByteArray(bufSize)
        var lastTs = System.nanoTime()
        var windowBytes = 0L
        input().use { ins ->
            output().use { outs ->
                while (true) {
                    val st = flow.value
                    if (st.cancelled) throw CancellationException("cancelled")
                    val n = ins.read(buf)
                    if (n < 0) break
                    outs.write(buf, 0, n)
                    windowBytes += n
                    val now = System.nanoTime()
                    val dt = now - lastTs
                    val bps = if (dt > 200_000_000L) {
                        val v = (windowBytes * 1_000_000_000L) / max(dt, 1L)
                        windowBytes = 0
                        lastTs = now
                        v
                    } else st.bytesPerSecond
                    flow.value = st.copy(
                        currentName = name,
                        bytesDone = st.bytesDone + n,
                        bytesPerSecond = bps,
                    )
                }
                outs.flush()
            }
        }
        val done = flow.value
        flow.value = done.copy(filesDone = done.filesDone + 1, currentName = name)
    }

    fun finish(jobId: String, error: String? = null) {
        jobs[jobId]?.let { it.value = it.value.copy(finished = true, error = error) }
    }

    suspend fun copyTree(jobId: String, src: File, dest: File) {
        if (src.isDirectory) {
            if (!dest.exists() && !dest.mkdirs()) error("Cannot create ${dest.path}")
            src.listFiles()?.forEach { child ->
                copyTree(jobId, child, File(dest, child.name))
            }
        } else {
            dest.parentFile?.mkdirs()
            copyFile(jobId, src.name, { src.inputStream() }, { dest.outputStream() }, src.length())
        }
    }

    companion object {
        fun bufferFor(size: Long): Int = when {
            size >= 64L * 1024 * 1024 -> 1024 * 1024
            size >= 8L * 1024 * 1024 -> 256 * 1024
            else -> 64 * 1024
        }

        fun speedLabel(bps: Long): String {
            if (bps < 1024) return "$bps B/s"
            if (bps < 1024 * 1024) return "${bps / 1024} KB/s"
            return String.format("%.1f MB/s", bps / (1024.0 * 1024.0))
        }
    }
}
