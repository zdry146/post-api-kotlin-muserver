package com.example.postapi.dto

/**
 * 统一 API 响应格式 — 与原版 ApiResult<T> 一致
 * Kotlin data class 替代 Lombok @Data @Builder
 */
data class ApiResult<T>(
    val code: Int = 200,
    val message: String = "success",
    val data: T? = null
) {
    companion object {
        fun <T> ok(data: T): ApiResult<T> = ApiResult(code = 200, message = "success", data = data)
        fun <T> ok(message: String, data: T?): ApiResult<T?> = ApiResult(code = 200, message = message, data = data)
        fun <T> fail(code: Int, message: String): ApiResult<T?> = ApiResult(code = code, message = message, data = null)
        fun <T> fail(message: String): ApiResult<T?> = fail(400, message)
    }
}