// Post API — Kotlin + mu-server 2.4.2
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// Azul Zulu 25 toolchain 自动下载（从 foojay-resolver-convention 仓库）
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "post-api-kotlin-muserver"