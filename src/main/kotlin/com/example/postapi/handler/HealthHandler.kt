package com.example.postapi.handler

import com.example.postapi.config.JsonMapper
import io.muserver.Method
import io.muserver.MuHandler
import io.muserver.MuResponse
import io.muserver.RouteHandler
import io.muserver.Routes
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * 健康检查 endpoint — 替代 Spring Boot Actuator /health 和 /health/ready
 *
 * - GET /health      : liveness，进程是否在运行。无依赖检查。固定 200 {"status":"ok"}。
 * - GET /health/ready: readiness，关键依赖（DB）是否可用。200/503 二态。
 *
 * 两端点分开的目的：
 * - Kubernetes liveness probe 调 /health，失败重启 pod
 * - Kubernetes readiness probe 调 /health/ready，失败移出 service endpoint
 *
 * DB 连接检查用 HikariCP ds.connection.isValid(timeoutSec)。
 * 故意只用 2 秒超时：probe 通常 < 5s，不能让 probe 卡死 orchestrator。
 */
class HealthHandler(private val dataSource: DataSource) {

    private val log = LoggerFactory.getLogger(HealthHandler::class.java)

    fun register(): List<MuHandler> = listOf(
        // ⚠️ exact path (/health/ready) 必须先于 wildcard {path} 类路由
        // 这里没有 {path} 兜底，但保留顺序注释以提醒后续维护者

        // GET /health — liveness（永远 200）
        Routes.route(Method.GET, "/health", RouteHandler { _, resp, _ ->
            writeHealthOk(resp)
        }),

        // GET /health/ready — readiness（DB 可用 → 200，否则 503）
        Routes.route(Method.GET, "/health/ready", RouteHandler { _, resp, _ ->
            checkReadiness(resp)
        })
    )

    private fun writeHealthOk(resp: MuResponse) {
        val body = mapOf("status" to "ok")
        resp.status(200)
        resp.contentType("application/json; charset=utf-8")
        resp.write(JsonMapper.writeValueAsString(body))
    }

    private fun checkReadiness(resp: MuResponse) {
        val dbAvailable = try {
            dataSource.connection.use { conn ->
                // isValid(2) 内部发送 JDBC 验证 query（PG: SELECT 1；H2: SELECT 1）
                conn.isValid(2)
            }
        } catch (ex: Throwable) {
            log.warn("Readiness DB check failed: {}", ex.message)
            false
        }

        if (dbAvailable) {
            val body = mapOf("status" to "ok", "db" to "up")
            resp.status(200)
            resp.contentType("application/json; charset=utf-8")
            resp.write(JsonMapper.writeValueAsString(body))
        } else {
            val body = mapOf("status" to "down", "db" to "down")
            resp.status(503)
            resp.contentType("application/json; charset=utf-8")
            resp.write(JsonMapper.writeValueAsString(body))
        }
    }
}
