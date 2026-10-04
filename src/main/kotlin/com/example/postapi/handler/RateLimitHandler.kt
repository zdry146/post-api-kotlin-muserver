package com.example.postapi.handler

import com.example.postapi.config.JsonMapper
import io.muserver.MuHandler
import io.muserver.MuRequest
import io.muserver.MuResponse
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-IP rate limit handler — 自实现的滑动窗口限流
 *
 * 为什么自实现而不用 mu-server 内置 RateLimit？
 * - mu-server 2.4.2 内置的 `RateLimit` 通过 Route.rateLimit 配置，粒度是单 route；
 *   我们要"应用到 /api/posts/ 下所有路径"，自实现更直接。
 * - 简单 per-IP 计数 + 滑动窗口策略，无需 token bucket 复杂度。
 *
 * 设计：
 * - 桶：`ConcurrentHashMap<String, Bucket>`，key = client IP
 * - Bucket：AtomicLong windowStartNanos + AtomicInteger count
 * - 每次请求：取客户端 IP（`req.clientIP()`，处理 X-Forwarded-For），检查桶；
 *   count >= limit 且未过窗 → 429；否则递增
 *
 * 适用：
 * - GET 列表高频路径不限制（已经加了 Cache-Control）；
 * - POST/PUT/DELETE 写路径要保护（防刷、减 DB 压力）。
 *
 * 配置通过 env 变量：
 * - RATE_LIMIT_PER_SECOND（默认 50）
 * - RATE_LIMIT_ENABLED（默认 true；prod 可设为 false 关掉做诊断）
 *
 * 用法：
 * ```
 * builder.addHandler(RateLimitHandler())     // 必须先注册
 * builder.addHandler(postHandler.register()) // 路由在 rate limit 之后注册
 * ```
 * 注意顺序：RateLimitHandler 必须在被保护的路由之前注册（mu-server 按注册顺序匹配）。
 */
class RateLimitHandler(
    private val requestsPerSecond: Int = (System.getenv("RATE_LIMIT_PER_SECOND")?.toIntOrNull() ?: 50),
    private val enabled: Boolean = (System.getenv("RATE_LIMIT_ENABLED")?.toBooleanStrictOrNull() ?: true)
) : MuHandler {

    private val log = LoggerFactory.getLogger(RateLimitHandler::class.java)
    private val buckets = ConcurrentHashMap<String, Bucket>()

    override fun handle(req: MuRequest, resp: MuResponse): Boolean {
        if (!enabled) return false
        if (!shouldLimit(req)) return false

        val ip = clientIp(req)
        val now = System.nanoTime()
        val bucket = buckets.computeIfAbsent(ip) { Bucket(now) }

        // 滑动窗口：若跨秒则重置 count
        val currentWindowStart = bucket.windowStartNanos.get()
        val windowElapsedNanos = now - currentWindowStart
        if (windowElapsedNanos >= TimeUnit.SECONDS.toNanos(1)) {
            // 用 CAS 重置窗口（多线程并发安全）
            if (bucket.windowStartNanos.compareAndSet(currentWindowStart, now)) {
                bucket.count.set(0)
            }
        }

        val current = bucket.count.incrementAndGet()
        if (current > requestsPerSecond) {
            // 触限：返回 429
            log.warn("Rate limit exceeded for IP {} (count={} > limit={})", ip, current, requestsPerSecond)
            resp.status(429)
            resp.headers().add("Retry-After", "1")
            resp.contentType("application/json; charset=utf-8")
            resp.write(JsonMapper.writeValueAsString(
                mapOf("code" to 429, "message" to "Too many requests, please slow down")
            ))
            return true
        }
        // 未触限：交给下一个 handler
        return false
    }

    /**
     * 决定是否对当前请求限流。
     * 匹配：写路径（POST/PUT/DELETE/PATCH）且 path 命中 /api/posts/ 下所有路径。
     * 注意 GET 路径不加限制（列表已有 Cache-Control，单条详情 + viewCount 是轻量读）。
     */
    private fun shouldLimit(req: MuRequest): Boolean {
        val method = req.method().name
        if (method !in WRITE_METHODS) return false
        val path = req.relativePath()
        // 仅限 /api/posts/* 写路径（含 /like /unlike /toggle-publish）
        return path.startsWith("/api/posts/")
    }

    /**
     * 解析客户端 IP。
     * mu-server 的 `clientIP()` 已处理 X-Forwarded-For（仅当 behind proxy + 配置开启时），
     * 否则用 remoteAddress()。fallback 到 "unknown"。
     */
    private fun clientIp(req: MuRequest): String {
        return try {
            req.clientIP() ?: req.remoteAddress() ?: "unknown"
        } catch (ex: Throwable) {
            "unknown"
        }
    }

    /** 测试/运维用：清空桶 */
    fun resetBuckets() {
        buckets.clear()
    }

    /** 测试/运维用：当前已知的 IP 桶数 */
    fun trackedIpCount(): Int = buckets.size

    /** 测试/运维用：当前 limit */
    fun getRequestsPerSecond(): Int = requestsPerSecond

    /** 测试/运维用：当前是否启用 */
    fun isEnabled(): Boolean = enabled

    /** 内部桶结构 */
    private class Bucket(initialWindowStartNanos: Long) {
        val windowStartNanos = AtomicLong(initialWindowStartNanos)
        val count = AtomicInteger(0)
    }

    companion object {
        private val WRITE_METHODS = setOf("POST", "PUT", "DELETE", "PATCH")
    }
}
