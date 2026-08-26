package com.sarchiver.app.data.archive

import com.github.junrar.Archive
import com.sarchiver.app.domain.ArchiveEntryInfo
import com.sarchiver.app.util.PathSecurity
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.Deflater

enum class ArchiveFormat { ZIP, SEVEN_Z, TAR, TAR_GZ, TAR_XZ, TAR_BZ2, GZ, XZ, BZ2, RAR }

object ArchiveEngine {

    fun detect(file: File): ArchiveFormat? {
        val n = file.name.lowercase()
        return when {
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> ArchiveFormat.TAR_GZ
            n.endsWith(".tar.xz") || n.endsWith(".txz") -> ArchiveFormat.TAR_XZ
            n.endsWith(".tar.bz2") || n.endsWith(".tbz2") -> ArchiveFormat.TAR_BZ2
            n.endsWith(".zip") -> ArchiveFormat.ZIP
            n.endsWith(".7z") -> ArchiveFormat.SEVEN_Z
            n.endsWith(".tar") -> ArchiveFormat.TAR
            n.endsWith(".gz") -> ArchiveFormat.GZ
            n.endsWith(".xz") -> ArchiveFormat.XZ
            n.endsWith(".bz2") -> ArchiveFormat.BZ2
            n.endsWith(".rar") -> ArchiveFormat.RAR
            else -> null
        }
    }

    fun canCreate(format: ArchiveFormat) = format != ArchiveFormat.RAR

    fun canEncrypt(format: ArchiveFormat) = format == ArchiveFormat.ZIP || format == ArchiveFormat.SEVEN_Z

    fun list(file: File): List<ArchiveEntryInfo> {
        return when (detect(file)) {
            ArchiveFormat.ZIP -> ZipFile.builder().setFile(file).get().use { z ->
                z.entries.toList().map {
                    ArchiveEntryInfo(it.name, it.isDirectory, it.size, it.compressedSize, it.time)
                }
            }
            ArchiveFormat.SEVEN_Z -> SevenZFile.builder().setFile(file).get().use { z ->
                z.entries.map {
                    ArchiveEntryInfo(it.name, it.isDirectory, it.size, it.size, it.lastModifiedDate?.time ?: 0L)
                }
            }
            ArchiveFormat.RAR -> Archive(file).use { a ->
                a.fileHeaders.map {
                    ArchiveEntryInfo(it.fileName, it.isDirectory, it.unpSize, it.fullPackSize, it.mTime?.time ?: 0L)
                }
            }
            ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_XZ, ArchiveFormat.TAR_BZ2 ->
                tarStream(file).use { tin ->
                    val out = mutableListOf<ArchiveEntryInfo>()
                    var e: TarArchiveEntry? = tin.nextEntry
                    while (e != null) {
                        out += ArchiveEntryInfo(e.name, e.isDirectory, e.size, e.size, e.modTime.time)
                        e = tin.nextEntry
                    }
                    out
                }
            else -> listOf(ArchiveEntryInfo(file.name.removeSuffixExt(), false, file.length(), file.length(), file.lastModified()))
        }
    }

    fun extractAll(archive: File, destDir: File, password: CharArray? = null) {
        destDir.mkdirs()
        when (detect(archive)) {
            ArchiveFormat.ZIP -> ZipFile.builder().setFile(archive).setUseUnicodeExtraFields(true).get().use { z ->
                if (password != null) z.setPassword(password)
                z.entries.toList().forEach { e ->
                    val target = PathSecurity.resolveSafe(destDir, e.name)
                    if (e.isDirectory) target.mkdirs()
                    else {
                        target.parentFile?.mkdirs()
                        z.getInputStream(e).use { ins -> target.outputStream().use { ins.copyTo(it) } }
                    }
                }
            }
            ArchiveFormat.SEVEN_Z -> {
                val b = SevenZFile.builder().setFile(archive)
                if (password != null) b.setPassword(password)
                b.get().use { z ->
                    var e: SevenZArchiveEntry? = z.nextEntry
                    while (e != null) {
                        val target = PathSecurity.resolveSafe(destDir, e.name)
                        if (e.isDirectory) target.mkdirs()
                        else {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { z.getInputStream(e).copyTo(it) }
                        }
                        e = z.nextEntry
                    }
                }
            }
            ArchiveFormat.RAR -> Archive(archive, password?.concatToString()).use { a ->
                a.fileHeaders.forEach { h ->
                    val target = PathSecurity.resolveSafe(destDir, h.fileName)
                    if (h.isDirectory) target.mkdirs()
                    else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { a.extractFile(h, it) }
                    }
                }
            }
            ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_XZ, ArchiveFormat.TAR_BZ2 ->
                tarStream(archive).use { tin ->
                    var e: TarArchiveEntry? = tin.nextEntry
                    while (e != null) {
                        val target = PathSecurity.resolveSafe(destDir, e.name)
                        if (e.isDirectory) target.mkdirs()
                        else {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { tin.copyTo(it) }
                        }
                        e = tin.nextEntry
                    }
                }
            ArchiveFormat.GZ -> singleDecompress(destDir, archive, GzipCompressorInputStream(archive.inputStream()))
            ArchiveFormat.XZ -> singleDecompress(destDir, archive, XZCompressorInputStream(archive.inputStream()))
            ArchiveFormat.BZ2 -> singleDecompress(destDir, archive, BZip2CompressorInputStream(archive.inputStream()))
            null -> error("Unsupported archive: ${archive.name}")
        }
    }

    fun createZip(sources: List<File>, dest: File, level: Int = Deflater.DEFAULT_COMPRESSION, password: CharArray? = null) {
        ZipArchiveOutputStream(dest).use { zos ->
            zos.setLevel(level.coerceIn(0, 9))
            if (password != null) {
                zos.setPassword(password)
                zos.setUseZip64(org.apache.commons.compress.archivers.zip.Zip64Mode.AsNeeded)
            }
            sources.forEach { addZip(zos, it, it.name) }
        }
    }

    fun create7z(sources: List<File>, dest: File) {
        SevenZOutputFile(dest).use { out ->
            sources.forEach { add7z(out, it, it.name) }
        }
    }

    fun createTar(sources: List<File>, dest: File, gzip: Boolean = false, xz: Boolean = false) {
        val raw = dest.outputStream().buffered()
        val wrapped = when {
            gzip -> GzipCompressorOutputStream(raw)
            xz -> XZCompressorOutputStream(raw)
            else -> raw
        }
        TarArchiveOutputStream(wrapped).use { tos ->
            tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            sources.forEach { addTar(tos, it, it.name) }
        }
    }

    fun testIntegrity(file: File): Boolean {
        return try {
            list(file)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun addZip(zos: ZipArchiveOutputStream, file: File, name: String) {
        if (file.isDirectory) {
            val e = ZipArchiveEntry(if (name.endsWith("/")) name else "$name/")
            zos.putArchiveEntry(e)
            zos.closeArchiveEntry()
            file.listFiles()?.forEach { addZip(zos, it, "$name/${it.name}") }
        } else {
            val e = ZipArchiveEntry(file, name)
            zos.putArchiveEntry(e)
            file.inputStream().use { it.copyTo(zos) }
            zos.closeArchiveEntry()
        }
    }

    private fun add7z(out: SevenZOutputFile, file: File, name: String) {
        if (file.isDirectory) {
            val e = out.createArchiveEntry(file, if (name.endsWith("/")) name else "$name/")
            out.putArchiveEntry(e)
            out.closeArchiveEntry()
            file.listFiles()?.forEach { add7z(out, it, "$name/${it.name}") }
        } else {
            val e = out.createArchiveEntry(file, name)
            out.putArchiveEntry(e)
            val buf = ByteArray(64 * 1024)
            file.inputStream().use { ins ->
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
            }
            out.closeArchiveEntry()
        }
    }

    private fun addTar(tos: TarArchiveOutputStream, file: File, name: String) {
        if (file.isDirectory) {
            val e = TarArchiveEntry(file, "$name/")
            tos.putArchiveEntry(e)
            tos.closeArchiveEntry()
            file.listFiles()?.forEach { addTar(tos, it, "$name/${it.name}") }
        } else {
            val e = TarArchiveEntry(file, name)
            tos.putArchiveEntry(e)
            file.inputStream().use { it.copyTo(tos) }
            tos.closeArchiveEntry()
        }
    }

    private fun tarStream(file: File): TarArchiveInputStream {
        val n = file.name.lowercase()
        val bis = BufferedInputStream(FileInputStream(file))
        val ins = when {
            n.endsWith(".gz") || n.endsWith(".tgz") -> GzipCompressorInputStream(bis)
            n.endsWith(".xz") || n.endsWith(".txz") -> XZCompressorInputStream(bis)
            n.endsWith(".bz2") || n.endsWith(".tbz2") -> BZip2CompressorInputStream(bis)
            else -> bis
        }
        return TarArchiveInputStream(ins)
    }

    private fun singleDecompress(destDir: File, archive: File, ins: java.io.InputStream) {
        val name = archive.name.removeSuffixExt()
        val target = PathSecurity.resolveSafe(destDir, name)
        target.parentFile?.mkdirs()
        ins.use { input -> target.outputStream().use { input.copyTo(it) } }
    }

    private fun String.removeSuffixExt(): String {
        return when {
            lowercase().endsWith(".tar.gz") -> dropLast(7)
            lowercase().endsWith(".tar.xz") -> dropLast(7)
            else -> substringBeforeLast('.')
        }
    }
}
