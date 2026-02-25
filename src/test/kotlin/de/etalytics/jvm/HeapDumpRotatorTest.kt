package de.etalytics.jvm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class HeapDumpRotatorTest {
    private val fixedClock = Clock.fixed(Instant.ofEpochSecond(1700000000L), ZoneOffset.UTC)

    @Test
    fun `rotates existing heap dump file`(
        @TempDir tempDir: Path,
    ) {
        val dumpFile = tempDir.resolve("heap.hprof").also { it.createFile() }
        val args = listOf("-XX:HeapDumpPath=$dumpFile")

        HeapDumpRotator(jvmArgs = args, clock = fixedClock).rotate()

        assertFalse(dumpFile.exists(), "Original dump file should have been renamed")
        val archived = tempDir.listDirectoryEntries().single()
        assertEquals("heap-1700000000.hprof", archived.name)
    }

    @Test
    fun `rotates heap dump file with %p placeholder`(
        @TempDir tempDir: Path,
    ) {
        val pid = 12345L
        val dumpFile = tempDir.resolve("heap-$pid.hprof").also { it.createFile() }
        val args = listOf("-XX:HeapDumpPath=${tempDir.resolve("heap-%p.hprof")}")

        HeapDumpRotator(jvmArgs = args, clock = fixedClock).rotate()

        assertFalse(dumpFile.exists(), "Original dump file should have been renamed")
        val archived = tempDir.listDirectoryEntries().single()
        assertEquals("heap-$pid-1700000000.hprof", archived.name)
    }

    @Test
    fun `does nothing when no matching dump file exists`(
        @TempDir tempDir: Path,
    ) {
        val args = listOf("-XX:HeapDumpPath=${tempDir.resolve("heap.hprof")}")

        HeapDumpRotator(jvmArgs = args, clock = fixedClock).rotate()

        assertTrue(tempDir.listDirectoryEntries().isEmpty())
    }

    @Test
    fun `does nothing when no HeapDumpPath JVM arg is present`(
        @TempDir tempDir: Path,
    ) {
        HeapDumpRotator(jvmArgs = emptyList(), clock = fixedClock).rotate()
        assertTrue(tempDir.listDirectoryEntries().isEmpty())
    }

    @Test
    fun `enforces retention policy by deleting oldest dumps`(
        @TempDir tempDir: Path,
    ) {
        val dumpPath = tempDir.resolve("heap.hprof")
        val args = listOf("-XX:HeapDumpPath=$dumpPath")

        // Create 4 pre-existing rotated dumps with different modification times
        val rotated1 = tempDir.resolve("heap-1000.hprof").also { it.createFile() }
        val rotated2 = tempDir.resolve("heap-2000.hprof").also { it.createFile() }
        val rotated3 = tempDir.resolve("heap-3000.hprof").also { it.createFile() }
        val rotated4 = tempDir.resolve("heap-4000.hprof").also { it.createFile() }

        // Set last-modified times so that rotated1 is oldest, rotated4 is newest
        rotated1.toFile().setLastModified(1000L)
        rotated2.toFile().setLastModified(2000L)
        rotated3.toFile().setLastModified(3000L)
        rotated4.toFile().setLastModified(4000L)

        HeapDumpRotator(maxRetainedDumps = 2, jvmArgs = args, clock = fixedClock).rotate()

        val remaining = tempDir.listDirectoryEntries().map { it.name }.toSet()
        assertFalse(remaining.contains(rotated1.name), "Oldest dump should be deleted")
        assertFalse(remaining.contains(rotated2.name), "Second oldest dump should be deleted")
        assertTrue(remaining.contains(rotated3.name), "Third dump should be retained")
        assertTrue(remaining.contains(rotated4.name), "Newest dump should be retained")
    }

    @Test
    fun `does not delete dumps when count is within retention limit`(
        @TempDir tempDir: Path,
    ) {
        val dumpPath = tempDir.resolve("heap.hprof")
        val args = listOf("-XX:HeapDumpPath=$dumpPath")

        val rotated1 = tempDir.resolve("heap-1000.hprof").also { it.createFile() }
        val rotated2 = tempDir.resolve("heap-2000.hprof").also { it.createFile() }

        HeapDumpRotator(maxRetainedDumps = 5, jvmArgs = args, clock = fixedClock).rotate()

        val remaining = tempDir.listDirectoryEntries().map { it.name }.toSet()
        assertTrue(remaining.contains(rotated1.name))
        assertTrue(remaining.contains(rotated2.name))
    }
}
