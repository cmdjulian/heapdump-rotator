plugins {
    kotlin("jvm") version "2.0.21"
    `maven-publish`
}

group = "de.etalytics.jvm"
version = "1.0.0"

kotlin {
    jvmToolchain(11)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
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
}
