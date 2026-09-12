package com.example.postapi

import com.example.postapi.batch.CleanupJob
import com.example.postapi.config.CorsConfig
import com.example.postapi.config.DatabaseFactory
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
 * 启动步骤：
 * 1. 创建 HikariCP DataSource
 * 2. 初始化 schema (schema-postgres.sql)
 * 3. 实例化 Repository / Service / Handler
 * 4. 配置 mu-server (CORS + routes + 启动参数)
 * 5. 启动 cleanup batch 定时任务
 * 6. 注册 shutdown hook（优雅关闭）
 */
object Application {

    private val log = LoggerFactory.getLogger(Application::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        log.info("=== post-api-kotlin-muserver starting ===")

        // 1. 数据库
        val ds = DatabaseFactory.create()
        DatabaseFactory.initializeSchema(ds, Paths.get("src/main/resources/schema-postgres.sql"))

        // 2. Repository
        val postRepository = PostRepository(ds)

        // 3. Service
        val postService = PostService(postRepository)

        // 4. Handler
        val postHandler = PostHandler(postService)
        val cleanupJob = CleanupJob(postRepository)
        val adminHandler = AdminHandler(cleanupJob)

        // 5. 启动 cleanup 定时任务（每天 0 点）
        cleanupJob.start()

        // 6. mu-server 配置
        val httpPort = System.getenv("HTTP_PORT")?.toIntOrNull() ?: 8080
        val server = MuServerBuilder.httpServer()
            .withHttpPort(httpPort)
            .withCORS(CorsConfig.create())  // CORS Handler（顶层）
            .addHandler(postHandler.register())  // 9 个 post endpoints
            .addHandler(adminHandler.register())  // cleanup-job trigger
            .withRequestTimeout(java.time.Duration.ofSeconds(10))
            .withIdleTimeout(java.time.Duration.ofSeconds(60))
            .withMaxHeadersSize(8192)
            .withMaxUrlSize(8192)
            .start()

        log.info("=== Server started at http://localhost:{} ===", httpPort)
        log.info("Endpoints:")
        log.info("  POST   /api/posts")
        log.info("  PUT    /api/posts/{{id}}")
        log.info("  GET    /api/posts/{{id}}")
        log.info("  DELETE /api/posts/{{id}}")
        log.info("  GET    /api/posts/published?page=&size=")
        log.info("  GET    /api/posts/all?page=&size=")
        log.info("  GET    /api/posts/search?keyword=&page=&size=")
        log.info("  POST   /api/posts/{{id}}/toggle-publish")
        log.info("  POST   /api/posts/{{id}}/like")
        log.info("  POST   /api/admin/cleanup-job")

        // 7. 优雅关闭
        Runtime.getRuntime().addShutdownHook(Thread {
            log.info("=== Shutting down ===")
            cleanupJob.stop()
            server.stop()
        })

        // 阻塞主线程直到 server 关闭
        Thread.currentThread().join()
    }
}