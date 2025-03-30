plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.3"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"

}

group = "dev.openrune"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://raw.githubusercontent.com/OpenRune/hosting/master")
    maven("https://jitpack.io")
}

dependencies {
    implementation("dev.or2:all:2.0.8")
    implementation("com.akuleshov7:ktoml-core:0.5.0")
    implementation("com.akuleshov7:ktoml-file:0.5.0")
    implementation("org.jetbrains.compose.ui:ui:1.0.0")
    implementation("org.jetbrains.compose.foundation:foundation:1.0.0")
    implementation("org.jetbrains.compose.material:material:1.0.0")
    implementation("org.jetbrains.compose.runtime:runtime:1.0.0")
    implementation("cc.ekblad:4koma:1.2.0")

}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2023.2.6")
    type.set("IC") // Target IDE Platform
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("242.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
