package com.example.postapi.config

import io.muserver.Method
import io.muserver.handlers.CORSHandler
import io.muserver.handlers.CORSHandlerBuilder

/**
 * CORS 配置 — 替代 Spring WebConfig addCorsMappings
 *
 * mu-server 2.4.2 API:
 * - CORSHandlerBuilder.corsHandler() 创建 builder
 * - .withCORSConfig(...) 设 config（config 来自 CORSHandlerBuilder.config() 或 rest.CORSConfigBuilder.corsConfig()）
 * - .withAllowedMethods(Method...) 设允许的 HTTP methods
 * - .build() 返回 CORSHandler (implements MuHandler)
 *
 * 安全策略（替代原版 "all origins allowed"）：
 * - 默认只允许本地前端开发 origin：http://localhost:5173 (Vite), http://localhost:3000 (CRA)
 * - 通过 env `CORS_ALLOWED_ORIGINS` 覆盖（逗号分隔）
 * - 设置 `CORS_ALLOWED_ORIGINS=*` 显式恢复旧行为（不推荐生产）
 */
object CorsConfig {

    private const val DEFAULT_ORIGINS = "http://localhost:5173,http://localhost:3000"
    private const val WILDCARD = "*"

    fun create(): CORSHandler {
        val configuredOrigins = (System.getenv("CORS_ALLOWED_ORIGINS") ?: DEFAULT_ORIGINS)
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val configBuilder = if (configuredOrigins.size == 1 && configuredOrigins[0] == WILDCARD) {
            // 显式 opt-in 通配（不推荐生产，仅用于诊断/本地调试）
            CORSHandlerBuilder.config().withAllOriginsAllowed()
        } else {
            CORSHandlerBuilder.config().withAllowedOrigins(configuredOrigins)
        }

        return CORSHandlerBuilder.corsHandler()
            .withCORSConfig(configBuilder)
            .withAllowedMethods(
                Method.GET, Method.POST, Method.PUT,
                Method.DELETE, Method.OPTIONS, Method.HEAD, Method.PATCH
            )
            .build()
    }
}
