
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("jvm") version "2.2.0"
    java
    idea
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "io.blurite"
version = "1.0"

repositories {
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
    gradlePluginPortal()
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    intellijPlatform {
        intellijIdeaCommunity("2025.2")

        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.toml.lang")
    }
}

// Add generated language classes source set
sourceSets {
    main {
        java {
            srcDir("src/main/gen")
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "OpenRune"
        version = "2025.2"
        changeNotes.set("Add support for comments in RSCM files")
    }

    pluginVerification {
        ides {
            // Test against minimum version to ensure compatibility
            create("IC", "2024.2")  // Minimum version (Kotlin 2.2 support)
            create("IC", "2025.2")  // Current target version
        }

        failureLevel.set(
            setOf(
                // These will make the task fail on common K2-incompatible patterns
                FailureLevel.COMPATIBILITY_PROBLEMS,
                FailureLevel.MISSING_DEPENDENCIES,
                FailureLevel.INTERNAL_API_USAGES,
                FailureLevel.DEPRECATED_API_USAGES
            )
        )
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
    }
}
