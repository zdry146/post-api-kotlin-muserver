package com.example.postapi.handler

import com.example.postapi.config.JsonMapper
import com.example.postapi.dto.ApiResult
import com.example.postapi.dto.CreatePostRequest
import com.example.postapi.dto.UpdatePostRequest
import com.example.postapi.exception.GlobalExceptionHandler
import com.example.postapi.exception.NotFoundException
import com.example.postapi.exception.ValidationException
import com.example.postapi.service.PostService
import io.muserver.Method
import io.muserver.MuRequest
import io.muserver.MuResponse
import io.muserver.RouteHandler
import io.muserver.Routes

/**
 * Post REST endpoints — 替代 Spring @RestController PostController
 *
 * 用 mu-server 2.4.2 的 Routes DSL 注册 9 个 endpoints（与原版完全一致）
 */
class PostHandler(private val postService: PostService) {

    /**
     * 注册所有 routes 到 MuServerBuilder
     */
    fun register(): List<Routes.Route> = listOf(
        // 创建帖子
        route(Method.POST, "/api/posts", ::create),

        // 更新帖子
        routeWithId(Method.PUT, "/api/posts/{id}", ::update),

        // 获取帖子（自动 +1 viewCount）
        routeWithId(Method.GET, "/api/posts/{id}", ::getById),

        // 软删除
        routeWithId(Method.DELETE, "/api/posts/{id}", ::delete),

        // 已发布列表
        route(Method.GET, "/api/posts/published", ::listPublished),

        // 全部分页
        route(Method.GET, "/api/posts/all", ::listAll),

        // 搜索
        route(Method.GET, "/api/posts/search", ::search),

        // 切换发布
        routeWithId(Method.POST, "/api/posts/{id}/toggle-publish", ::togglePublish),

        // 点赞
        routeWithId(Method.POST, "/api/posts/{id}/like", ::like)
    )

    // ============ Handler 包装 ============

    /**
     * 注册一个 route，自动 try/catch 异常转 JSON 响应
     */
    private fun route(method: Method, path: String, fn: (MuRequest, Map<String, String>) -> Unit): Routes.Route {
        return Routes.route(method, path, RouteHandler { req, resp, params ->
            try {
                fn(req, params)
            } catch (ex: Throwable) {
                GlobalExceptionHandler.handle(ex, resp)
                writeJsonResponse(GlobalExceptionHandler.handle(ex, resp), resp)
            }
        })
    }

    /**
     * 注册带 {id} 路径参数的 route（解析为 Long）
     */
    private fun routeWithId(method: Method, path: String, fn: (MuRequest, Long, Map<String, String>) -> Unit): Routes.Route {
        return Routes.route(method, path, RouteHandler { req, resp, params ->
            try {
                val id = parseLongId(params["id"])
                fn(req, id, params)
            } catch (ex: Throwable) {
                GlobalExceptionHandler.handle(ex, resp)
                writeJsonResponse(GlobalExceptionHandler.handle(ex, resp), resp)
            }
        })
    }

    // ============ Handler 实现（与原 Spring 注解版 1:1 对应） ============

    private fun create(req: MuRequest, params: Map<String, String>) {
        val body = req.readBodyAsString() ?: throw ValidationException("请求体不能为空")
        val request = JsonMapper.readValue<CreatePostRequest>(body)
        val result = postService.create(request)
        writeJsonResponse(ApiResult.ok("帖子创建成功", result), req)
    }

    private fun update(req: MuRequest, id: Long, params: Map<String, String>) {
        val body = req.readBodyAsString() ?: throw ValidationException("请求体不能为空")
        val request = JsonMapper.readValue<UpdatePostRequest>(body)
        val result = postService.update(id, request)
        writeJsonResponse(ApiResult.ok("帖子更新成功", result), req)
    }

    private fun getById(req: MuRequest, id: Long, params: Map<String, String>) {
        val post = postService.getById(id)
        writeJsonResponse(ApiResult.ok(post), req)
    }

    private fun delete(req: MuRequest, id: Long, params: Map<String, String>) {
        postService.delete(id)
        writeJsonResponse(ApiResult.ok<Void>("帖子删除成功", null), req)
    }

    private fun listPublished(req: MuRequest, params: Map<String, String>) {
        val (page, size) = parsePageable(req)
        val result = postService.listPublished(page, size)
        writeJsonResponse(ApiResult.ok(result), req)
    }

    private fun listAll(req: MuRequest, params: Map<String, String>) {
        val (page, size) = parsePageable(req)
        val result = postService.listAll(page, size)
        writeJsonResponse(ApiResult.ok(result), req)
    }

    private fun search(req: MuRequest, params: Map<String, String>) {
        val keyword = req.queryParameter("keyword") ?: throw ValidationException("keyword 参数必填")
        val (page, size) = parsePageable(req)
        val result = postService.search(keyword, page, size)
        writeJsonResponse(ApiResult.ok(result), req)
    }

    private fun togglePublish(req: MuRequest, id: Long, params: Map<String, String>) {
        val post = postService.togglePublish(id)
        writeJsonResponse(ApiResult.ok("发布状态已切换", post), req)
    }

    private fun like(req: MuRequest, id: Long, params: Map<String, String>) {
        val post = postService.like(id)
        writeJsonResponse(ApiResult.ok("点赞成功", post), req)
    }

    // ============ 工具方法 ============

    private fun parseLongId(idStr: String?): Long {
        if (idStr.isNullOrBlank()) throw ValidationException("id 不能为空")
        return idStr.toLongOrNull() ?: throw ValidationException("id 必须是数字: $idStr")
    }

    /**
     * 解析 ?page=&size= 参数
     */
    private fun parsePageable(req: MuRequest): Pair<Int, Int> {
        val page = req.queryParameter("page")?.toIntOrNull() ?: 0
        val size = req.queryParameter("size")?.toIntOrNull() ?: 10
        require(page >= 0) { "page 必须 >= 0" }
        require(size in 1..100) { "size 必须在 1..100 之间" }
        return page to size
    }

    private fun writeJsonResponse(result: ApiResult<*>, req: MuRequest) {
        val resp = req.response()
        resp.status = result.code
        resp.contentType("application/json; charset=utf-8")
        resp.write(JsonMapper.writeValueAsString(result))
    }
}

/**
** 读取 MuRequest.queryParameter 扩展
 */
private fun MuRequest.queryParameter(name: String): String? {
    return this.query().firstOrNull(name)
}