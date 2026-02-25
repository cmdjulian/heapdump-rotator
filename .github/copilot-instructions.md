# Copilot Instructions for heapdump-rotator

## Project Overview

`heapdump-rotator` is a **zero-dependency Kotlin JVM library** that rotates JVM heap dump files (`.hprof`) at application startup to prevent file collisions when a Kubernetes pod restarts after an `OutOfMemoryError`. The HotSpot JVM does not support a `%t` timestamp placeholder in `-XX:HeapDumpPath=`, so without rotation the JVM silently skips writing a new dump if the file already exists.

The library supports an optional FIFO retention policy and is designed for seamless interoperability for both Kotlin and Java consumers.

## Tech Stack

| Concern       | Choice                                   |
|---------------|------------------------------------------|
| Language      | Kotlin 2.2+                              |
| Target JVM    | Java 11 (configured via Gradle toolchain) |
| Build system  | Gradle with Kotlin DSL (`build.gradle.kts`) |
| Testing       | JUnit 5 (Jupiter)                        |
| Dependencies  | `kotlin-stdlib` only — no frameworks     |

## Project Layout

```
heapdump-rotator/
├── .github/
│   └── copilot-instructions.md      # This file
├── src/
│   ├── main/kotlin/de/etalytics/jvm/
│   │   └── HeapDumpRotator.kt       # Core library class
│   └── test/kotlin/de/etalytics/jvm/
│       └── HeapDumpRotatorTest.kt   # JUnit 5 tests
├── build.gradle.kts                 # Gradle build with kotlin-jvm, maven-publish
├── settings.gradle.kts              # Root project name: heapdump-rotator
└── README.md
```

## Building and Testing

```bash
# Build (compile + test)
gradle build --no-daemon

# Run tests only
gradle test --no-daemon

# Publish to local Maven repo
gradle publishToMavenLocal --no-daemon
```

Always use `--no-daemon` to avoid daemon-related issues in CI.

## Core Class: `HeapDumpRotator`

Located at `src/main/kotlin/de/etalytics/jvm/HeapDumpRotator.kt`.

**Constructor parameters** (all have defaults, annotated with `@JvmOverloads`):
- `maxRetainedDumps: Int = 0` — max rotated dumps to keep; `0` means unlimited
- `jvmArgs: List<String>` — defaults to `ManagementFactory.getRuntimeMXBean().inputArguments`
- `clock: Clock` — defaults to `Clock.systemUTC()`

**Key behavior of `rotate()`**:
1. Finds `-XX:HeapDumpPath=<path>` in `jvmArgs`; returns silently if absent
2. Returns silently if the target directory does not exist
3. Handles the `%p` (PID) placeholder by building a regex with `Regex.escape()` and `\d+`
4. Renames matching `.hprof` files by injecting `<epochSecond>` before the extension
5. If `maxRetainedDumps > 0`, deletes the oldest rotated dumps (sorted by `lastModifiedTime`) to enforce the limit

## Code Style & Conventions

- Use `@JvmOverloads` on constructors with default parameters so Java callers get clean primitive-type overloads
- Use primitive `Int` (not `Int?`) for numeric configuration parameters — avoids boxed `Integer` in Java interop
- Use `Files.list(...).use { ... }` with explicit type annotations when collecting Java streams to Kotlin lists (the Kotlin `.toList()` extension does not apply to `java.util.stream.Stream`)
- Collect Java streams via `stream.collect(java.util.stream.Collectors.toList())` or assign explicit `List<Path>` types

## Testing Conventions

- Use `@TempDir` (JUnit 5) for all file-system tests
- Use `Clock.fixed(Instant.ofEpochSecond(...), ZoneOffset.UTC)` for deterministic timestamps
- Use `Files.setLastModifiedTime(file, FileTime.fromMillis(ts))` to control sort order in retention-policy tests
- Test cases to always include: standard rotation, `%p` placeholder rotation, retention policy enforcement, graceful no-op when `-XX:HeapDumpPath` is absent, graceful no-op when the directory does not exist

## Publishing (JitPack)

The `maven-publish` plugin is configured. JitPack picks up the library from the GitHub release tag automatically — no extra configuration required.
