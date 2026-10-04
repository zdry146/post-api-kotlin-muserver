package com.example.postapi.handler

import io.muserver.Method
import io.muserver.MuHandler
import io.muserver.MuResponse
import io.muserver.RouteHandler
import io.muserver.Routes
import io.muserver.handlers.ResourceHandlerBuilder

/**
 * Swagger UI documentation endpoint — /docs serves interactive API docs.
 *
 * 实现：自定义 3 个路由
 * - GET /docs         → redirect to /docs/index.html
 * - GET /docs/index.html → serve static HTML from classpath:static/docs/index.html
 *
 * 为什么不直接用 ResourceHandler？
 *   - ResourceHandlerBuilder.classpathHandler("/static/docs/") 会把 URL 路径暴露为 /static/docs/...
 *   - 那用户要访问 /static/docs/index.html，不符合 /docs 的契约
 *   - 用自定义路由 + 显式读取 classpath resource，能精确控制 URL
 *
 * 路由顺序：/docs (exact) 先于 /docs/{file}，避免 /docs 被吞。
 */
object DocsHandler {

    private const val CLASSPATH_BASE = "/static/docs/"

    fun register(): List<MuHandler> = listOf(
        // GET /docs → 301 redirect to /docs/index.html
        Routes.route(Method.GET, "/docs", RouteHandler { _, resp, _ ->
            resp.status(301)
            resp.headers().add("Location", "/docs/index.html")
            resp.write("")
        }),

        // GET /docs/index.html → 读 classpath 资源，写出
        Routes.route(Method.GET, "/docs/index.html", RouteHandler { _, resp, _ ->
            val body = readResource("index.html")
            if (body == null) {
                resp.status(404)
                resp.contentType("text/plain; charset=utf-8")
                resp.write("docs not found")
            } else {
                resp.status(200)
                resp.contentType("text/html; charset=utf-8")
                resp.write(body)
            }
        })
    )

    private fun readResource(name: String): String? {
        val path = CLASSPATH_BASE + name
        return DocsHandler::class.java.getResourceAsStream(path)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }
}
