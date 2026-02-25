# heapdump-rotator

A zero-dependency JVM utility to automatically rotate and retain OOM heap dumps on startup, preventing file collisions in Docker and Kubernetes.

## The Problem

When a JVM application running in Kubernetes crashes with an `OutOfMemoryError`, the HotSpot JVM writes a heap dump to the path configured via `-XX:HeapDumpPath=`. When the pod restarts (as Kubernetes does automatically), the JVM tries to write a new heap dump to the **same path** — but the file already exists, so **the new dump is silently skipped**.

Unlike some enterprise JVMs, HotSpot does not support a `%t` (timestamp) placeholder in `-XX:HeapDumpPath=`, so there is no built-in way to avoid collisions. This library solves the problem by rotating any existing `.hprof` file at application startup, before the JVM can write a new one.

## Features

- **Zero-dependency** — only `kotlin-stdlib`, no frameworks required
- **`%p` placeholder support** — handles paths like `-XX:HeapDumpPath=/dumps/crash-%p.hprof`
- **FIFO retention policy** — optionally keep only the N most recent dumps to control disk usage
- **Seamless Java & Kotlin interop** — clean API for both languages

## Installation

Add the JitPack repository and the dependency to your build:

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.cmdjulian:heapdump-rotator:1.0.0")
}
```

### Gradle (Groovy DSL)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.cmdjulian:heapdump-rotator:1.0.0'
}
```

## Usage

Call `HeapDumpRotator.rotate()` as early as possible during application startup — **before** the framework initialises — so the existing dump is moved before the JVM has a chance to refuse writing a new one.

### Kotlin

```kotlin
import de.etalytics.jvm.HeapDumpRotator

fun main(args: Array<String>) {
    // Rotate any existing heap dump and keep at most 5 rotated files
    HeapDumpRotator(maxRetainedDumps = 5).rotate()

    // Then start your application normally, e.g. Spring Boot:
    // SpringApplication.run(MyApp::class.java, *args)
}
```

### Java

```java
import de.etalytics.jvm.HeapDumpRotator;

public class Application {
    public static void main(String[] args) {
        // Clean Java interop — no need to pass all parameters
        new HeapDumpRotator(5).rotate();

        // SpringApplication.run(Application.class, args);
    }
}
```

## Configuration

| Parameter          | Type           | Default                        | Description                                                                               |
|--------------------|----------------|--------------------------------|-------------------------------------------------------------------------------------------|
| `maxRetainedDumps` | `Int?`         | `null` (unlimited)             | Maximum number of rotated `.hprof` files to keep. Oldest files are deleted when exceeded. |
| `jvmArgs`          | `List<String>` | `RuntimeMXBean.inputArguments` | JVM arguments to scan for `-XX:HeapDumpPath=`. Override for testing.                     |
| `clock`            | `Clock`        | `Clock.systemUTC()`            | Clock used to generate the rotation timestamp. Override for testing.                      |

## How It Works

1. On startup, `rotate()` reads the JVM arguments and looks for `-XX:HeapDumpPath=<path>`.
2. If the argument is absent or the target directory does not exist, it exits silently.
3. Any `.hprof` files matching the configured path (including `%p` PID placeholders) are **renamed** by injecting a UTC epoch-second timestamp before the extension, e.g. `java_pid12345.hprof` → `java_pid12345-1700000000.hprof`.
4. If `maxRetainedDumps` is set, the oldest rotated dumps are deleted until the count is within the limit.

## JVM Configuration

Configure the JVM to write heap dumps to a known location:

```
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/dumps/crash.hprof
```

Or using the `%p` PID placeholder (useful when multiple JVMs share a dump directory):

```
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/dumps/crash-%p.hprof
```

## License

MIT
