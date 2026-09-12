package com.example.postapi.config

import io.muserver.Method
import io.muserver.handlers.CORSHandler
import io.muserver.handlers.CORSHandlerBuilder

/**
 * CORS 配置 — 替代 Spring WebConfig addCorsMappings
 *
 * mu-server 2.4.2 API:
 * - CORSHandlerBuilder.corsHandler() 创建 builder
 * - .withCORSConfig(CORSHandlerBuilder.config().withAllOriginsAllowed()) 设 config
 * - .withAllowedMethods(Method...) 设允许的 HTTP methods
 * - .build() 返回 CORSHandler (implements MuHandler)
 */
object CorsConfig {
    fun create(): CORSHandler {
        return CORSHandlerBuilder.corsHandler()
            .withCORSConfig(CORSHandlerBuilder.config().withAllOriginsAllowed())
            .withAllowedMethods(
                Method.GET, Method.POST, Method.PUT,
                Method.DELETE, Method.OPTIONS, Method.HEAD, Method.PATCH
            )
            .build()
    }
}