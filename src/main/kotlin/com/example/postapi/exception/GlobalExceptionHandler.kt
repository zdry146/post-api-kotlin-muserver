package com.example.postapi.exception

import com.example.postapi.dto.ApiResult
import io.muserver.MuRequest
import io.muserver.MuResponse
import org.slf4j.LoggerFactory

/**
 * 全局异常处理 — 替代 Spring @RestControllerAdvice GlobalExceptionHandler
 *
 * mu-server 通过 exchange.fireException(ex) 抛出，handler chain 中下一个 handler 捕获
 * 这里提供一个静态方法让 handler 显式调用以转换异常为 ApiResult JSON
 */
object GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    fun handle(ex: Throwable, response: MuResponse): MuResponse {
        val (status, apiResult) = when (ex) {
            is NotFoundException -> 404 to ApiResult.fail<Void>(404, ex.message ?: "Not found")
            is BusinessException -> ex.errorCode to ApiResult.fail<Void>(ex.errorCode, ex.message ?: "Business error")
            is ValidationException -> 400 to ApiResult.fail<Void>(400, ex.message ?: "Validation failed")
            is IllegalArgumentException -> 400 to ApiResult.fail<Void>(400, ex.message ?: "Invalid argument")
            else -> {
                log.error("Unexpected error", ex)
                500 to ApiResult.fail<Void>(500, "系统异常，请稍后重试")
            }
        }
        response.status = status
        return response
    }

    /**
     * 将 ApiResult 序列化为 JSON 写入响应
     */
    fun writeJson(result: ApiResult<*>, response: MuResponse) {
        response.contentType("application/json; charset=utf-8")
        response.write(JsonMapper.writeValueAsString(result))
    }
}