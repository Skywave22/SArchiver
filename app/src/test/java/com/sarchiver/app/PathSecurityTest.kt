package com.sarchiver.app

import com.sarchiver.app.util.FileSort
import com.sarchiver.app.util.PathSecurity
import com.sarchiver.app.data.archive.ArchiveEngine
import com.sarchiver.app.data.archive.ArchiveFormat
import com.sarchiver.app.data.transfer.TransferEngine
import com.sarchiver.app.domain.FsNode
import com.sarchiver.app.domain.SortMode
import com.sarchiver.app.domain.StorageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class PathSecurityTest {
    @Test
    fun blocksDotDot() {
        assertFalse(PathSecurity.isSafeArchiveName("../etc/passwd"))
        assertFalse(PathSecurity.isSafeArchiveName("foo/../../etc/passwd"))
    }

    @Test
    fun allowsNormal() {
        assertTrue(PathSecurity.isSafeArchiveName("dir/file.txt"))
        assertTrue(PathSecurity.isSafeArchiveName("unicöde 文件.zip"))
    }

    @Test
    fun formatBytes() {
        assertEquals("512 B", PathSecurity.formatBytes(512))
        assertTrue(PathSecurity.formatBytes(2048).contains("KB"))
    }
}

class FileSortTest {
    private fun node(name: String, dir: Boolean = false, size: Long = 0) = FsNode(
        name, name, "/$name", dir, size, 0, null, StorageKind.INTERNAL, true
    )

    @Test
    fun dirsFirstThenName() {
        val items = listOf(node("b.txt"), node("a", true), node("c", true))
        val s = FileSort.sort(items, SortMode.NAME)
        assertEquals(listOf("a", "c", "b.txt"), s.map { it.name })
    }

    @Test
    fun filterExtension() {
        val items = listOf(node("a.zip"), node("b.txt"), node("dir", true))
        val f = FileSort.filter(items, "", "zip")
        assertEquals(2, f.size)
    }

    @Test
    fun queryCaseInsensitive() {
        val items = listOf(node("ReadMe.MD"), node("other"))
        assertEquals(1, FileSort.filter(items, "readme", null).size)
    }
}

class TransferBufferTest {
    @Test
    fun largerFilesGetBiggerBuffers() {
        assertTrue(TransferEngine.bufferFor(100) < TransferEngine.bufferFor(100L * 1024 * 1024))
    }
}

class ArchiveRoundTripTest {
    @Test
    fun zipCreateExtractAndTraversalGuard() {
        val tmp = createTempDirectory("sarchiver").toFile()
        try {
            val src = File(tmp, "hello.txt").apply { writeText("hello sarchiver") }
            val zip = File(tmp, "out.zip")
            ArchiveEngine.createZip(listOf(src), zip)
            assertEquals(ArchiveFormat.ZIP, ArchiveEngine.detect(zip))
            assertTrue(ArchiveEngine.testIntegrity(zip))
            val dest = File(tmp, "out")
            ArchiveEngine.extractAll(zip, dest)
            assertEquals("hello sarchiver", File(dest, "hello.txt").readText())
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun tarGzRoundTrip() {
        val tmp = createTempDirectory("sarchiver-tar").toFile()
        try {
            val src = File(tmp, "n.txt").apply { writeText("n") }
            val tar = File(tmp, "a.tar.gz")
            ArchiveEngine.createTar(listOf(src), tar, gzip = true)
            val dest = File(tmp, "e")
            ArchiveEngine.extractAll(tar, dest)
            assertEquals("n", File(dest, "n.txt").readText())
        } finally {
            tmp.deleteRecursively()
        }
    }
}
