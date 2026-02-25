# heapdump-rotator

A zero-dependency JVM utility to automatically rotate and retain OOM heap dumps on startup, preventing file collisions in Docker and Kubernetes.

## The Problem

When a JVM application crashes with an `OutOfMemoryError` in Kubernetes, it typically writes a heap dump to a path configured via `-XX:HeapDumpPath=/dumps/heap.hprof`. Kubernetes then restarts the container. On the next startup the JVM finds an existing file at that path and **overwrites it**, destroying the previous crash evidence before you can retrieve it.

The JVM supports a `%p` (PID) placeholder in `HeapDumpPath`, but in containers PIDs are often recycled and not stable. The JVM does **not** support a `%t` (timestamp) placeholder for heap dumps like it does for GC logs, so there is no built-in way to avoid collisions.

## The Solution

`heapdump-rotator` solves this by running a small piece of code at application startup, **before** the JVM could write a new dump. It:

1. Reads the `-XX:HeapDumpPath` JVM argument from the running process.
2. Finds any existing dump file(s) matching that path pattern.
3. Renames (rotates) them to include a Unix timestamp, e.g. `heap.hprof` → `heap-1700000000.hprof`.
4. Optionally enforces a maximum retention count, deleting the oldest dumps (FIFO).

## Installation via JitPack

Add JitPack to your repositories and the library as a dependency:

**Gradle (Kotlin DSL)**
```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.cmdjulian:heapdump-rotator:1.0.0")
}
```

**Gradle (Groovy DSL)**
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.cmdjulian:heapdump-rotator:1.0.0'
}
```

## Usage

Call `HeapDumpRotator.rotate()` as early as possible in your application's entry point, before any framework initialisation. This ensures that any existing heap dump is archived before the JVM could potentially overwrite it on a new OOM.

**Kotlin example (Spring Boot)**
```kotlin
import de.etalytics.jvm.HeapDumpRotator
import org.springframework.boot.runApplication

fun main(args: Array<String>) {
    // Rotate any existing heap dump before Spring Boot starts.
    // Keep at most 5 archived dumps; delete older ones automatically.
    HeapDumpRotator(maxRetainedDumps = 5).rotate()

    runApplication<MyApplication>(*args)
}
```

**JVM flag required**
```
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps/heap.hprof
```

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `maxRetainedDumps` | `Int?` | `null` | Maximum number of archived dumps to keep. `null` means unlimited. |
| `jvmArgs` | `List<String>` | JVM input arguments | Override for testing. Reads from `ManagementFactory` by default. |
| `clock` | `Clock` | `Clock.systemUTC()` | Override for testing. |

## How It Works

1. Reads `-XX:HeapDumpPath=<path>` from JVM arguments.
2. Lists all files in the parent directory that match the dump file name (including `%p` PID placeholder support).
3. Renames matched files to `<name>-<epochSeconds><ext>`.
4. If `maxRetainedDumps` is set, removes the oldest archived dumps exceeding the limit.

All errors are caught and logged as warnings so the library never prevents your application from starting.

## Logging

`heapdump-rotator` uses `java.util.logging` (JUL), which is part of the JDK and requires no additional dependencies.

| Event | Level |
|---|---|
| Heap dump archived (renamed) | `INFO` |
| Old dump deleted (retention policy) | `INFO` |
| Directory missing / no `-XX:HeapDumpPath` arg | `FINE` |
| Exception during rotation | `WARNING` |

The logger name is `de.etalytics.jvm.HeapDumpRotator`.

### Behaviour when called before Spring Boot initializes

The recommended usage is to call `rotate()` **before** `runApplication<MyApplication>(*args)`, which means it runs before Spring installs the `jul-to-slf4j` bridge. At that point:

- JUL logs `INFO` and above directly to its own `ConsoleHandler` (always visible on the console).
- Spring's `logging.level` configuration in `application.yaml` or `logback.xml` does **not** apply to this early call, since the bridge is not yet active.

This is intentional — rotation must happen before the JVM could potentially overwrite an existing dump. The `INFO` messages confirm that rotation occurred and are always visible regardless of Spring's log configuration.

