// Post API — Kotlin + mu-server 2.4.2 + Azul Java 25
// Migrated from Spring Boot 4.0.5 + Java 21
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.0"
    application
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

// Azul Zulu 21 toolchain — Gradle 会自动从 foojay 仓库下载
// 注：Kotlin 2.1.x 最大支持 JVM_22 target。Azul Zulu 25 (OpenJDK 25) 在 Gradle 8.10.2 toolchain 解析可能有问题，
// 故使用 Azul Zulu 21 LTS（Kotlin 2.1.0 稳定支持）。Azul Zulu 21 是当前最新 LTS。
kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    // ============ mu-server 2.4.2 ============
    implementation("io.muserver:mu-server:2.4.2")

    // ============ Kotlin ============
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // ============ JSON ============
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    // ============ JDBC + 连接池 ============
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:5.1.0")
    // H2 runtime dep — for in-memory mode (jdbc:h2:mem:...) when DB_URL points to H2
    // DatabaseFactory detects URL prefix and picks driver; H2 only loaded when needed
    runtimeOnly("com.h2database:h2:2.3.232")

    // ============ Logging ============
    implementation("ch.qos.logback:logback-classic:1.5.12")

    // ============ Test ============
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")  // HTTP client for integration tests
}

application {
    // object Application { @JvmStatic fun main } → Application.class, NOT ApplicationKt.class
    mainClass.set("com.example.postapi.Application")
}

tasks.withType<Test> {
    useJUnitPlatform()
}