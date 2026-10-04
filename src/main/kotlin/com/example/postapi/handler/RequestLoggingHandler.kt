package com.example.postapi.handler

import io.muserver.MuHandler
import io.muserver.MuRequest
import io.muserver.MuResponse
import org.slf4j.LoggerFactory

/**
 * Request logging handler — wraps a MuHandler to log request + response + duration.
 *
 * Why custom? mu-server 2.4.2 doesn't have a built-in RequestLogHandler in handlers package.
 * - mu-server built-in `MuStats` exposes aggregate counters (requests handled, status counts)
 *   but doesn't provide per-request logs.
 * - The PostService / PostHandler already do SLF4J logging internally, but those don't include
 *   end-to-end duration or status code.
 *
 * Behavior:
 * - Records start time (System.nanoTime())
 * - Delegates to wrapped handler
 * - In `finally`, logs: `METHOD /path -> STATUS (DURATION ms) from IP`
 *
 * Usage:
 * ```
 * postHandler.register().map { RequestLoggingHandler(it) }.forEach { builder.addHandler(it) }
 * ```
 *
 * Env controls:
 * - REQUEST_LOG_ENABLED (default true)
 *
 * Note: logging in finally runs even when wrapped handler throws, but if response was already
 * written by the wrapped handler we still log the final status.
 */
class RequestLoggingHandler(
    private val next: MuHandler,
    private val enabled: Boolean = (System.getenv("REQUEST_LOG_ENABLED")?.toBooleanStrictOrNull() ?: true)
) : MuHandler {

    private val log = LoggerFactory.getLogger(RequestLoggingHandler::class.java)

    override fun handle(req: MuRequest, resp: MuResponse): Boolean {
        if (!enabled) return next.handle(req, resp)

        val startNanos = System.nanoTime()
        var handled = false
        try {
            handled = next.handle(req, resp)
        } finally {
            val durationMs = (System.nanoTime() - startNanos) / 1_000_000
            val method = req.method().name
            val path = req.relativePath()
            val status = try { resp.status() } catch (ex: Throwable) { 0 }
            val clientIp = try { req.clientIP() ?: req.remoteAddress() ?: "-" } catch (ex: Throwable) { "-" }
            log.info("{} {} -> {} ({} ms) from {}", method, path, status, durationMs, clientIp)
        }
        return handled
    }

    companion object {
        /** Read REQUEST_LOG_ENABLED env (shared across all wrapper instances) */
        fun isEnabledFromEnv(): Boolean =
            System.getenv("REQUEST_LOG_ENABLED")?.toBooleanStrictOrNull() ?: true

        fun wrapAll(handlers: List<MuHandler>): List<MuHandler> {
            val enabled = isEnabledFromEnv()
            return handlers.map { RequestLoggingHandler(it, enabled) }
        }
    }
}
