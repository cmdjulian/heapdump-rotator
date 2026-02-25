package de.etalytics.jvm

import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.time.Clock

/**
 * Rotates JVM heap dump files (`.hprof`) on application startup to prevent file collisions
 * when a Kubernetes pod restarts after an OutOfMemoryError.
 *
 * @param maxRetainedDumps optional maximum number of rotated dump files to retain (FIFO policy)
 * @param jvmArgs the JVM arguments to scan for `-XX:HeapDumpPath=`; defaults to the current process arguments
 * @param clock clock used to generate the rotation timestamp; defaults to UTC system clock
 */
class HeapDumpRotator @JvmOverloads constructor(
    private val maxRetainedDumps: Int? = null,
    private val jvmArgs: List<String> = ManagementFactory.getRuntimeMXBean().inputArguments,
    private val clock: Clock = Clock.systemUTC()
) {

    /**
     * Scans the heap dump path configured via `-XX:HeapDumpPath=`, renames any existing `.hprof`
     * files by injecting a UTC epoch-second timestamp before the extension, and optionally
     * enforces a retention limit by deleting the oldest rotated dumps.
     */
    fun rotate() {
        val heapDumpArg = jvmArgs.firstOrNull { it.startsWith(HEAP_DUMP_PATH_PREFIX) } ?: return
        val rawPath = heapDumpArg.removePrefix(HEAP_DUMP_PATH_PREFIX)

        val dumpPath = Paths.get(rawPath)

        // If the path points to a specific file (possibly with %p), determine the directory
        val dir: Path
        val fileNamePattern: Regex

        if (!Files.isDirectory(dumpPath)) {
            val parent = dumpPath.parent ?: Paths.get(".")
            if (!Files.isDirectory(parent)) return
            dir = parent
            val fileName = dumpPath.fileName.toString()
            fileNamePattern = buildPattern(fileName)
        } else {
            dir = dumpPath
            fileNamePattern = Regex(".*\\.hprof$")
        }

        // Find and rotate matching .hprof files
        val timestamp = clock.instant().epochSecond
        val rotatedFiles = mutableListOf<Path>()

        Files.list(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .filter { fileNamePattern.matches(it.fileName.toString()) }
                .forEach { file ->
                    val name = file.fileName.toString()
                    val rotatedName = injectTimestamp(name, timestamp)
                    val rotatedPath = file.resolveSibling(rotatedName)
                    Files.move(file, rotatedPath)
                    rotatedFiles.add(rotatedPath)
                }
        }

        // Enforce retention policy if configured
        if (maxRetainedDumps != null && maxRetainedDumps > 0) {
            enforceRetentionPolicy(dir, maxRetainedDumps)
        }
    }

    private fun buildPattern(fileName: String): Regex {
        return if (PROCESS_ID_PLACEHOLDER in fileName) {
            val parts = fileName.split(PROCESS_ID_PLACEHOLDER)
            val escapedParts = parts.map { Regex.escape(it) }
            Regex(escapedParts.joinToString("\\d+"))
        } else {
            Regex(Regex.escape(fileName))
        }
    }

    private fun injectTimestamp(fileName: String, timestamp: Long): String {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex >= 0) {
            "${fileName.substring(0, dotIndex)}-$timestamp${fileName.substring(dotIndex)}"
        } else {
            "$fileName-$timestamp"
        }
    }

    private fun enforceRetentionPolicy(dir: Path, maxRetained: Int) {
        val rotatedPattern = Regex(".*-\\d+\\.hprof$")
        val rotatedDumps: List<Path> = Files.list(dir).use { stream ->
            stream.filter { path -> Files.isRegularFile(path) }
                .filter { path -> rotatedPattern.matches(path.fileName.toString()) }
                .sorted(Comparator.comparing { path: Path ->
                    Files.readAttributes(path, BasicFileAttributes::class.java).lastModifiedTime()
                })
                .collect(java.util.stream.Collectors.toList())
        }

        val toDelete = rotatedDumps.size - maxRetained
        if (toDelete > 0) {
            rotatedDumps.take(toDelete).forEach { path -> Files.deleteIfExists(path) }
        }
    }

    private companion object {
        private const val HEAP_DUMP_PATH_PREFIX = "-XX:HeapDumpPath="
        private const val PROCESS_ID_PLACEHOLDER = "%p"
    }
}
