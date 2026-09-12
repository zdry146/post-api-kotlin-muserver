// Post API — Kotlin + mu-server 2.4.2 + Azul Java 25
// Migrated from Spring Boot 4.0.5 + Java 21
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "1.9.25"
    application
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

// Azul Zulu 25 toolchain — Gradle 会自动从 foojay 仓库下载
kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)  // 字节码目标（mu-server 2.4.2 source=1.8，最高兼容到 17）
        freeCompilerArgs.add("-Xjsr305=strict")  // 严格 null 检查（与 Java 互操作）
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
}

dependencies {
    // ============ mu-server 2.4.2 ============
    implementation("io.muserver:muserver:2.4.2")

    // ============ Kotlin ============
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // ============ JSON ============
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")  // Java 8 时间

    // ============ JDBC + 连接池 ============
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // ============ Validation ============
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
    implementation("org.glassfish:jakarta.el:4.0.2")

    // ============ Logging ============
    implementation("ch.qos.logback:logback-classic:1.5.12")

    // ============ Test ============
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
}

application {
    mainClass.set("com.example.postapi.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}