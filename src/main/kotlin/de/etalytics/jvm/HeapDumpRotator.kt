package de.etalytics.jvm

import java.lang.management.ManagementFactory
import java.nio.file.Path
import java.time.Clock
import java.util.logging.Logger
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

class HeapDumpRotator
    @JvmOverloads
    constructor(
        private val maxRetainedDumps: Int? = null,
        private val jvmArgs: List<String> = ManagementFactory.getRuntimeMXBean().inputArguments,
        private val clock: Clock = Clock.systemUTC(),
    ) {
        private val logger = Logger.getLogger(HeapDumpRotator::class.java.name)

        fun rotate() {
            try {
                val dumpArg =
                    jvmArgs.find { it.startsWith("-XX:HeapDumpPath=") } ?: run {
                        logger.fine("No -XX:HeapDumpPath JVM argument found, skipping heap dump rotation")
                        return
                    }
                val dumpPath = Path(dumpArg.substringAfter("="))
                val parentDir = dumpPath.parent ?: Path(".")

                if (!parentDir.exists() || !parentDir.isDirectory()) {
                    logger.fine("Heap dump directory does not exist: $parentDir, skipping rotation")
                    return
                }

                val ext = if (dumpPath.extension.isNotEmpty()) ".${dumpPath.extension}" else ""
                val parts = dumpPath.name.split("%p")
                val regexPattern = parts.joinToString("\\d+") { Regex.escape(it) }

                val exactRegex = Regex("^$regexPattern$")
                val nameParts = dumpPath.nameWithoutExtension.split("%p")
                val baseNamePattern = nameParts.joinToString("\\d+") { Regex.escape(it) }
                val rotatedRegex = Regex("^$baseNamePattern-\\d+$ext$")

                // 1. Rotate current crashes out of the way
                parentDir.listDirectoryEntries().forEach { file ->
                    if (exactRegex.matches(file.name)) {
                        val timestamp = clock.instant().epochSecond
                        val archivedFile = file.resolveSibling("${file.nameWithoutExtension}-$timestamp$ext")
                        file.moveTo(archivedFile, overwrite = true)
                        logger.info("Archived previous JVM heap dump: ${file.name} -> ${archivedFile.name}")
                    }
                }

                // 2. Enforce retention policy (FIFO)
                if (maxRetainedDumps != null && maxRetainedDumps > 0) {
                    val archivedDumps =
                        parentDir.listDirectoryEntries()
                            .filter { rotatedRegex.matches(it.name) }
                            .sortedBy { it.getLastModifiedTime() }

                    logger.fine("Found ${archivedDumps.size} rotated heap dumps, retention limit is $maxRetainedDumps")

                    val dumpsToDelete = archivedDumps.size - maxRetainedDumps
                    if (dumpsToDelete > 0) {
                        archivedDumps.take(dumpsToDelete).forEach { file ->
                            file.deleteIfExists()
                            logger.info("Deleted old heap dump to enforce retention policy: ${file.name}")
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warning("Could not process heap dumps: ${e.message}")
            }
        }
    }
