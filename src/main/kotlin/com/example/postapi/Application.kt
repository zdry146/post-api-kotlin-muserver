package com.example.postapi

import com.example.postapi.batch.CleanupJob
import com.example.postapi.handler.AdminHandler
import com.example.postapi.handler.DocsHandler
import com.example.postapi.handler.HealthHandler
import com.example.postapi.handler.OpenApiHandler
import com.example.postapi.handler.PostHandler
import com.example.postapi.handler.RateLimitHandler
import com.example.postapi.handler.RequestLoggingHandler
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
 *   export CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
 *   export RATE_LIMIT_PER_SECOND=50
 *   export REQUEST_LOG_ENABLED=true
 *   ./gradlew run
 *
 * 端点（11 + 3 ops = 14）：
 *   POST   /api/posts
 *   PUT    /api/posts/{id}
 *   GET    /api/posts/{id}
 *   DELETE /api/posts/{id}
 *   GET    /api/posts/published?page=&size=
 *   GET    /api/posts/all?page=&size=
 *   GET    /api/posts/search?keyword=&page=&size=
 *   POST   /api/posts/{id}/toggle-publish
 *   POST   /api/posts/{id}/like
 *   POST   /api/posts/{id}/unlike
 *   POST   /api/admin/cleanup-job
 *
 * Ops 端点：
 *   GET    /health            — liveness
 *   GET    /health/ready      — readiness (DB check)
 *   GET    /openapi.json      — OpenAPI spec
 *
 * 注册顺序很关键（mu-server 按注册顺序匹配，第一个 wins）：
 *   1. CORSHandler         — preflight 优先级最高
 *   2. RateLimitHandler    — 必须在 post 路由之前（保护写路径）
 *   3. exact path routes  — /health/ready, /api/posts/published 等先于 {id} wildcard
 *   4. wildcard routes    — /api/posts/{id}
 *   5. RequestLoggingHandler 包装上面所有路由做 per-request 日志
 */
object Application {

    private val log = LoggerFactory.getLogger(Application::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        log.info("=== post-api-kotlin-muserver starting ===")

        // 1. 数据库
        val ds = com.example.postapi.config.DatabaseFactory.create()
        // DatabaseFactory.initializeSchema auto-detects schema file from DB type
        // (H2 → schema-h2.sql, Postgres → schema-postgres.sql) via classpath resource.
        com.example.postapi.config.DatabaseFactory.initializeSchema(ds)

        // 2. Repository / Service / Handler
        val postRepository = PostRepository(ds)
        val postService = PostService(postRepository)
        val postHandler = PostHandler(postService)
        val cleanupJob = CleanupJob(postRepository)
        val adminHandler = AdminHandler(cleanupJob)
        val healthHandler = HealthHandler(ds)

        // 3. 启动 cleanup 定时任务（每天 0 点）
        cleanupJob.start()

        // 4. mu-server 配置 — 默认 8090（Jenkins 占 :8080），用 HTTP_PORT env var 可覆盖
        val httpPort = System.getenv("HTTP_PORT")?.toIntOrNull() ?: 8090
        val builder = MuServerBuilder.httpServer().withHttpPort(httpPort)

        // 4a. CORS（preflight 优先级最高）
        builder.addHandler(com.example.postapi.config.CorsConfig.create())

        // 4b. Rate limit handler — per-IP, applied to /api/posts/* write paths
        val rateLimitHandler = RateLimitHandler()
        builder.addHandler(rateLimitHandler)
        log.info("Rate limit: enabled={}, limit={}/sec", rateLimitHandler.isEnabled(), rateLimitHandler.getRequestsPerSecond())

        // 4c. Routes (exact path first, wildcard last)
        // - OpenAPI spec endpoint (exact, public)
        OpenApiHandler.register().forEach { builder.addHandler(it) }
        // - Health endpoints
        healthHandler.register().forEach { builder.addHandler(it) }
        DocsHandler.register().forEach { builder.addHandler(it) }
        // - Post + Admin endpoints (wrap with request logging)
        val postHandlers = postHandler.register()
        val adminHandlers = adminHandler.register()
        val allRoutes = postHandlers + adminHandlers
        val loggedRoutes = RequestLoggingHandler.wrapAll(allRoutes)
        loggedRoutes.forEach { builder.addHandler(it) }

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
        log.info("  POST   /api/posts/{id}/unlike")
        log.info("  POST   /api/admin/cleanup-job")
        log.info("  GET    /health")
        log.info("  GET    /health/ready")
        log.info("  GET    /openapi.json")

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
