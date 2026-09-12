// Post API — Kotlin + mu-server 2.4.2
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT)
    repositories {
        mavenCentral()
        // Gradle toolchain 仓库（自动下载 Azul Zulu 25）
        // 已默认包含在 Gradle 8.5+ 中
    }
}

rootProject.name = "post-api-kotlin-muserver"