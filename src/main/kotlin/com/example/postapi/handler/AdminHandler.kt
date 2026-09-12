package com.example.postapi.handler

import com.example.postapi.batch.CleanupJob
import com.example.postapi.config.JsonMapper
import com.example.postapi.dto.ApiResult
import io.muserver.Method
import io.muserver.MuRequest
import io.muserver.RouteHandler
import io.muserver.Routes

/**
 * Admin endpoints — 替代 Spring @RestController CleanupJobController
 *
 * 手动触发 cleanup batch job（替代原版 Spring Batch JobOperator.start）
 */
class AdminHandler(private val cleanupJob: CleanupJob) {

    fun register(): List<Routes.Route> = listOf(
        Routes.route(Method.POST, "/api/admin/cleanup-job", RouteHandler { req, resp, _ ->
            try {
                cleanupJob.runNow()
                resp.status = 200
                resp.contentType("application/json; charset=utf-8")
                resp.write(JsonMapper.writeValueAsString(ApiResult.ok<String>("Cleanup job triggered successfully", null)))
            } catch (ex: Throwable) {
                resp.status = 500
                resp.contentType("application/json; charset=utf-8")
                resp.write(JsonMapper.writeValueAsString(ApiResult.fail<String>(500, ex.message ?: "Internal error")))
            }
        })
    )
}