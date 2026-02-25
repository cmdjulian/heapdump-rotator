package de.etalytics.jvm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class HeapDumpRotatorTest {

    private val fixedTimestamp = 1700000000L
    private val fixedClock = Clock.fixed(Instant.ofEpochSecond(fixedTimestamp), ZoneOffset.UTC)

    @Test
    fun `rotates standard crash hprof file`(@TempDir tempDir: Path) {
        val dumpFile = tempDir.resolve("java_pid12345.hprof")
        Files.createFile(dumpFile)

        val rotator = HeapDumpRotator(
            jvmArgs = listOf("-XX:HeapDumpPath=${tempDir.resolve("java_pid12345.hprof")}"),
            clock = fixedClock
        )
        rotator.rotate()

        assertFalse(Files.exists(dumpFile), "Original file should be renamed")
        val rotated = tempDir.resolve("java_pid12345-$fixedTimestamp.hprof")
        assertTrue(Files.exists(rotated), "Rotated file should exist")
    }

    @Test
    fun `rotates file using percent-p placeholder`(@TempDir tempDir: Path) {
        val dumpFile = tempDir.resolve("crash-42.hprof")
        Files.createFile(dumpFile)

        val rotator = HeapDumpRotator(
            jvmArgs = listOf("-XX:HeapDumpPath=${tempDir.resolve("crash-%p.hprof")}"),
            clock = fixedClock
        )
        rotator.rotate()

        assertFalse(Files.exists(dumpFile), "Original file should be renamed")
        val rotated = tempDir.resolve("crash-42-$fixedTimestamp.hprof")
        assertTrue(Files.exists(rotated), "Rotated file should exist")
    }

    @Test
    fun `enforces retention policy by deleting oldest dumps`(@TempDir tempDir: Path) {
        // Create 4 pre-existing rotated dumps (already have timestamps in their names)
        // with distinct modification times so the retention ordering is deterministic
        val timestamps = listOf(1000L, 2000L, 3000L, 4000L)
        val rotatedFiles = timestamps.map { ts ->
            val file = tempDir.resolve("crash-$ts.hprof")
            Files.createFile(file)
            Files.setLastModifiedTime(file, FileTime.fromMillis(ts))
            file
        }

        // jvmArgs points to a pattern that doesn't match the pre-existing files,
        // so rotate() won't rename them — it will only apply the retention policy
        val rotator = HeapDumpRotator(
            maxRetainedDumps = 2,
            jvmArgs = listOf("-XX:HeapDumpPath=${tempDir.resolve("java_pid-%p.hprof")}"),
            clock = fixedClock
        )
        rotator.rotate()

        // The two oldest (ts=1000, ts=2000) should be deleted by the retention policy
        assertFalse(Files.exists(rotatedFiles[0]), "Oldest dump should be deleted")
        assertFalse(Files.exists(rotatedFiles[1]), "Second oldest dump should be deleted")
        assertTrue(Files.exists(rotatedFiles[2]), "Third dump should be retained")
        assertTrue(Files.exists(rotatedFiles[3]), "Newest dump should be retained")
    }

    @Test
    fun `exits gracefully when no HeapDumpPath argument is provided`(@TempDir tempDir: Path) {
        val rotator = HeapDumpRotator(
            jvmArgs = listOf("-Xmx512m", "-Xms256m"),
            clock = fixedClock
        )
        // Should not throw any exception
        assertDoesNotThrow { rotator.rotate() }
        // Directory should remain empty
        val files = Files.list(tempDir).use { it.collect(java.util.stream.Collectors.toList()) }
        assertTrue(files.isEmpty(), "No files should be created")
    }

    @Test
    fun `exits gracefully when HeapDumpPath directory does not exist`(@TempDir tempDir: Path) {
        val nonExistent = tempDir.resolve("nonexistent/crash.hprof")
        val rotator = HeapDumpRotator(
            jvmArgs = listOf("-XX:HeapDumpPath=$nonExistent"),
            clock = fixedClock
        )
        assertDoesNotThrow { rotator.rotate() }
    }
}
