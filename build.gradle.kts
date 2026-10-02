plugins {
    kotlin("jvm") version "2.4.20"
    `maven-publish`
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "com.github.cmdjulian"
version = project.findProperty("projectVersion")?.toString() ?: "1.0.0"

kotlin {
    jvmToolchain(11)
}

java {
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name = "heapdump-rotator"
                description = "A zero-dependency JVM utility to automatically rotate and retain OOM heap dumps on startup."
                url = "https://github.com/cmdjulian/heapdump-rotator"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                        distribution = "repo"
                    }
                }
                scm {
                    url = "https://github.com/cmdjulian/heapdump-rotator"
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/cmdjulian/heapdump-rotator")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
