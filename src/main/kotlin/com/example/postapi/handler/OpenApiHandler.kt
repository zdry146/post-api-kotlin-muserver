package com.example.postapi.handler

import io.muserver.Method
import io.muserver.MuHandler
import io.muserver.MuResponse
import io.muserver.RouteHandler
import io.muserver.Routes

/**
 * OpenAPI spec endpoint — 把 openapi-spec.json 暴露为 /openapi.json
 *
 * Spec 文件来自原版 Spring Boot 项目（拷贝自 ~/claudecode-workspace/java-projects/post-api/openapi-tests/openapi-spec.json），
 * 位于 classpath: /openapi-spec.json。生成时通过 build.gradle 的 resources 拷贝机制自动打包。
 *
 * 路由：GET /openapi.json（exact path），始终先于任何 /api/ 星号 注册以避免被吞。
 *
 * CORS：浏览器/工具（如 Stoplight Elements、Postman）从其他 origin 拉 spec 时需要 CORS。
 * 这里用通配 不是安全问题——spec 是公开文档，不含密钥。
 */
object OpenApiHandler {

    private const val SPEC_RESOURCE_PATH = "/openapi-spec.json"
    private const val CACHE_MAX_AGE_SECONDS = 300L  // 5 分钟 — spec 不常变

    fun register(): List<MuHandler> {
        val specBytes: ByteArray = loadSpecBytes()
        val specBody = String(specBytes, Charsets.UTF_8)

        return listOf(
            Routes.route(Method.GET, "/openapi.json", RouteHandler { req, resp, _ ->
                // CORS headers（覆盖 CorsConfig 配置：spec 是公开的，允许所有 origin）
                val origin = req.headers().get("Origin")
                if (origin != null) {
                    resp.headers().add("Access-Control-Allow-Origin", origin)
                    resp.headers().add("Vary", "Origin")
                    resp.headers().add("Access-Control-Allow-Methods", "GET, OPTIONS")
                    resp.headers().add("Access-Control-Allow-Headers", "Content-Type")
                }
                resp.headers().add("Cache-Control", "public, max-age=$CACHE_MAX_AGE_SECONDS")
                resp.contentType("application/json; charset=utf-8")
                resp.status(200)
                resp.write(specBody)
            }),

            // CORS preflight for /openapi.json
            Routes.route(Method.OPTIONS, "/openapi.json", RouteHandler { req, resp, _ ->
                val origin = req.headers().get("Origin")
                if (origin != null) {
                    resp.headers().add("Access-Control-Allow-Origin", origin)
                    resp.headers().add("Vary", "Origin")
                    resp.headers().add("Access-Control-Allow-Methods", "GET, OPTIONS")
                    resp.headers().add("Access-Control-Allow-Headers", "Content-Type")
                    resp.headers().add("Access-Control-Max-Age", "86400")
                }
                resp.status(204)
            })
        )
    }

    private fun loadSpecBytes(): ByteArray {
        val stream = OpenApiHandler::class.java.getResourceAsStream(SPEC_RESOURCE_PATH)
            ?: error("openapi-spec.json not found on classpath at $SPEC_RESOURCE_PATH")
        return stream.use { it.readBytes() }
    }
}
