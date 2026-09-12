package com.example.postapi

import com.example.postapi.batch.CleanupJob
import com.example.postapi.handler.AdminHandler
import com.example.postapi.handler.PostHandler
import com.example.postapi.repository.PostRepository
import com.example.postapi.service.PostService
import io.muserver.MuServerBuilder
import org.slf4j.LoggerFactory
import java.nio.file.Paths

/**
 * Application 入口 — 替代 Spring Boot @SpringBootApplication PostApiApplication
 *
 * 用法:
 *   export DB_URL=jdbc:postgresql://localhost:5432/testdb
 *   export DB_USER=postgres
 *   export DB_PASSWORD=***
 *   ./gradlew run
 */
object Application {

    private val log = LoggerFactory.getLogger(Application::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        log.info("=== post-api-kotlin-muserver starting ===")

        // 1. 数据库
        val ds = com.example.postapi.config.DatabaseFactory.create()
        com.example.postapi.config.DatabaseFactory.initializeSchema(
            ds,
            Paths.get("src/main/resources/schema-postgres.sql")
        )

        // 2. Repository / Service / Handler
        val postRepository = PostRepository(ds)
        val postService = PostService(postRepository)
        val postHandler = PostHandler(postService)
        val cleanupJob = CleanupJob(postRepository)
        val adminHandler = AdminHandler(cleanupJob)

        // 3. 启动 cleanup 定时任务（每天 0 点）
        cleanupJob.start()

        // 4. mu-server 配置（最简化：只设端口 + handlers，timeout 用默认值）
        val httpPort = System.getenv("HTTP_PORT")?.toIntOrNull() ?: 8080
        val builder = MuServerBuilder.httpServer().withHttpPort(httpPort)
        postHandler.register().forEach { builder.addHandler(it) }   // 9 个 post endpoints
        adminHandler.register().forEach { builder.addHandler(it) }  // cleanup-job trigger
        val server = builder.start()

        log.info("=== Server started at {} ===", server.uri())
        log.info("Endpoints:")
        log.info("  POST   /api/posts")
        log.info("  PUT    /api/posts/{id}")
        log.info("  GET    /api/posts/{id}")
        log.info("  DELETE /api/posts/{id}")
        log.info("  GET    /api/posts/published?page=&size=")
        log.info("  GET    /api/posts/all?page=&size=")
        log.info("  GET    /api/posts/search?keyword=&page=&size=")
        log.info("  POST   /api/posts/{id}/toggle-publish")
        log.info("  POST   /api/posts/{id}/like")
        log.info("  POST   /api/admin/cleanup-job")

        // 5. 优雅关闭
        Runtime.getRuntime().addShutdownHook(Thread {
            log.info("=== Shutting down ===")
            cleanupJob.stop()
            server.stop()
        })

        // 阻塞主线程
        Thread.currentThread().join()
    }
}