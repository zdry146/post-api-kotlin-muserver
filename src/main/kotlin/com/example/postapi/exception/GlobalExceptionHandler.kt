package com.example.postapi.exception

import com.example.postapi.config.JsonMapper
import com.example.postapi.dto.ApiResult
import io.muserver.MuResponse
import org.slf4j.LoggerFactory

/**
 * 全局异常处理 — 替代 Spring @RestControllerAdvice GlobalExceptionHandler
 *
 * mu-server 2.4.2 中 MuResponse.status 是方法 status(int)，不是 property
 */
object GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    fun handle(ex: Throwable, response: MuResponse): Int {
        val status = when (ex) {
            is NotFoundException -> 404
            is BusinessException -> ex.errorCode
            is ValidationException -> 400
            is IllegalArgumentException -> 400
            else -> {
                log.error("Unexpected error", ex)
                500
            }
        }
        return status
    }

    fun writeError(ex: Throwable, response: MuResponse) {
        val status = handle(ex, response)
        System.err.println("[GlobalExceptionHandler] status=$status type=${ex.javaClass.name} msg=${ex.message}")
        ex.printStackTrace(System.err)
        val apiResult = when (ex) {
            is NotFoundException -> ApiResult.fail<Any>(404, ex.message ?: "Not found")
            is BusinessException -> ApiResult.fail<Any>(ex.errorCode, ex.message ?: "Business error")
            is ValidationException -> ApiResult.fail<Any>(400, ex.message ?: "Validation failed")
            is IllegalArgumentException -> ApiResult.fail<Any>(400, ex.message ?: "Invalid argument")
            else -> ApiResult.fail<Any>(500, "[${ex.javaClass.simpleName}] ${ex.message ?: "系统异常"}")
        }
        try {
            response.status(status)
            response.contentType("application/json; charset=utf-8")
            response.write(JsonMapper.writeValueAsString(apiResult))
        } catch (writeEx: Throwable) {
            System.err.println("[GlobalExceptionHandler] FAILED to write error response: ${writeEx.message}")
            writeEx.printStackTrace(System.err)
        }
    }
}