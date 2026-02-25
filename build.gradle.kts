plugins {
    kotlin("jvm") version "2.2.0"
    `maven-publish`
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
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
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
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
