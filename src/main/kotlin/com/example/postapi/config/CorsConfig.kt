package com.example.postapi.config

import io.muserver.CORSHandlerBuilder

/**
 * CORS 配置 — 替代 Spring WebConfig addCorsMappings
 *
 * mu-server 用内置 CORSHandlerBuilder（与 Spring Spring.mvc.cors 等价）
 */
object CorsConfig {
    fun create(): io.muserver.CORSHandler {
        return CORSHandlerBuilder()
            .withAllowedOriginPatterns("*")
            .withAllowedMethods("*")
            .withAllowedHeaders("*")
            .withExposedHeaders("*")
            .withMaxAgeSeconds(3600)
            .build()
    }
}