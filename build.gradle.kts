plugins {
    java
    id("com.gradleup.shadow") version "9.4.2"
}

group = "dev.melishy"
version = "1.0.0"
description = "XP based economy for Paper. Inspired by XPConomy."

java {
    // Build with JDK 25, but emit Java 21 bytecode (see compileJava.release below)
    // so the jar still loads on Java 21 servers (MC 1.20.5 - 1.21.x) as well as Java 25 ones.
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") // Paper API
    maven("https://jitpack.io")                                // VaultAPI
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") // PlaceholderAPI
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit")
    }
    compileOnly("me.clip:placeholderapi:2.11.6")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        // Don't raise this: Java 21 servers can't load classes compiled for a newer release.
        options.release = 21
    }

    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        minimize()
    }

    build {
        dependsOn(shadowJar)
    }
}
