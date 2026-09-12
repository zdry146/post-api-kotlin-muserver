package com.example.postapi.handler

import com.example.postapi.config.JsonMapper
import com.example.postapi.dto.ApiResult
import com.example.postapi.dto.CreatePostRequest
import com.example.postapi.dto.UpdatePostRequest
import com.example.postapi.exception.GlobalExceptionHandler
import com.example.postapi.exception.ValidationException
import com.example.postapi.service.PostService
import io.muserver.Method
import io.muserver.MuHandler
import io.muserver.MuRequest
import io.muserver.MuResponse
import io.muserver.RouteHandler
import io.muserver.Routes

/**
 * Post REST endpoints — 替代 Spring @RestController PostController
 *
 * mu-server 2.4.2 Routes DSL: Routes.route(method, path, RouteHandler { req, resp, params -> ... })
 */
class PostHandler(private val postService: PostService) {

    fun register(): List<MuHandler> = listOf(
        // ⚠️ 路由顺序很关键：mu-server Routes DSL 按注册顺序匹配第一个 wins。
        // list 端点（exact path）必须先于 {id} wildcard 注册，
        // 否则 /api/posts/published 会被 /api/posts/{id} 吃掉（id=published 解析失败）。

        // POST /api/posts — 创建
        Routes.route(Method.POST, "/api/posts", RouteHandler { req, resp, _ ->
            try {
                val body = req.readBodyAsString() ?: throw ValidationException("请求体不能为空")
                val request = JsonMapper.readValue<CreatePostRequest>(body)
                val result = postService.create(request)
                writeJson(ApiResult.ok("帖子创建成功", result), resp)
            } catch (ex: Throwable) { GlobalExceptionHandler.writeError(ex, resp) }
        }),

        // PUT /api/posts/{id} — 更新
        Routes.route(Method.PUT, "/api/posts/{id}", RouteHandler { req, resp, params ->
            try {
                val id = parseId(params["id"])
                val body = req.readBodyAsString() ?: throw ValidationException("请求体不能为空")
                val request = JsonMapper.readValue<UpdatePostRequest>(body)
                val result = postService.update(id, request)
                writeJson(ApiResult.ok("帖子更新成功", result), resp)
            } catch (ex: Throwable) { GlobalExceptionHandler.writeError(ex, resp) }
        }),

        // DELETE /api/posts/{id} — 软删除
        Routes.route(Method.DELETE, "/api/posts/{id}", RouteHandler { req, resp, params ->
            try {
                val id = parseId(params["id"])
                postService.delete(id)
                writeJson(ApiResult.ok<Any>("帖子删除成功", null), resp)
            } catch (ex: Throwable) { GlobalExceptionHandler.writeError(ex, resp) }
        }),

        // GET /api/posts/published — 已发布分页（exact path，必须先于 {id}）
        Routes.route(Method.GET, "/api/posts/published", RouteHandler { req, resp, _ ->
            try {
                val (page, size) = parsePageable(req)
                val result = postService.listPublished(page, size)
                writeJson(ApiResult.ok(result), resp)
            } catch (ex: Throwable) { GlobalExceptionHandler.writeError(ex, resp) }
        }),

        // GET /api/posts/all — 全部分页（exact path，必须先于 {id}）
        Routes.route(Method.GET, "/api/posts/all", RouteHandler { req, resp, _ ->
            try {
                val (page, size) = parsePageable(req)
                val result = postService.listAll(page, size)
                writeJson(ApiResult.ok(result), resp)
            } catch (ex: Throwable) { GlobalExceptionHandler.writeError(ex, resp) }
        }),

        // GET /api/posts/search — 标题搜索（exact path，必须先于 {id}）
        Routes.route(Method.GET, "/api/posts/search", RouteHandler { req, resp, _ ->
            try {
                val keyword = req.query().get("keyword")
                    ?: throw ValidationException("keyword 参数必填")
                val (page, size) = parsePageable(req)
                val result = postService.search(keyword, page, size)
                writeJson(ApiResult.ok(result), resp)
            } catch (ex: Throwable) { GlobalExceptionHandler.writeError(ex, resp) }
        }),

        // GET /api/posts/{id} — 获取（+1 viewCount）— 放在最后兜底，避免吃掉 list 端点
        Routes.route(Method.GET, "/api/posts/{id}", RouteHandler { req, resp, params ->
            try {
                val id = parseId(params["id"])
                val post = postService.getById(id)
                writeJson(ApiResult.ok(post), resp)
            } catch (ex: Throwable) { GlobalExceptionHandler.writeError(ex, resp) }
        }),

        // POST /api/posts/{id}/toggle-publish
        Routes.route(Method.POST, "/api/posts/{id}/toggle-publish", RouteHandler { req, resp, params ->
            try {
                val id = parseId(params["id"])
                val post = postService.togglePublish(id)
                writeJson(ApiResult.ok("发布状态已切换", post), resp)
            } catch (ex: Throwable) { GlobalExceptionHandler.writeError(ex, resp) }
        }),

        // POST /api/posts/{id}/like
        Routes.route(Method.POST, "/api/posts/{id}/like", RouteHandler { req, resp, params ->
            try {
                val id = parseId(params["id"])
                val post = postService.like(id)
                writeJson(ApiResult.ok("点赞成功", post), resp)
            } catch (ex: Throwable) { GlobalExceptionHandler.writeError(ex, resp) }
        })
    )

    private fun parseId(idStr: String?): Long {
        if (idStr.isNullOrBlank()) throw ValidationException("id 不能为空")
        return idStr.toLongOrNull() ?: throw ValidationException("id 必须是数字: $idStr")
    }

    private fun parsePageable(req: MuRequest): Pair<Int, Int> {
        val page = req.query().get("page")?.toIntOrNull() ?: 0
        val size = req.query().get("size")?.toIntOrNull() ?: 10
        require(page >= 0) { "page 必须 >= 0" }
        require(size in 1..100) { "size 必须在 1..100 之间" }
        return page to size
    }

    private fun writeJson(result: ApiResult<*>, resp: MuResponse) {
        resp.status(result.code)
        resp.contentType("application/json; charset=utf-8")
        resp.write(JsonMapper.writeValueAsString(result))
    }
}