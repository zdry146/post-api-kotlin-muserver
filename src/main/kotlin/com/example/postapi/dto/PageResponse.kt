package com.example.postapi.dto

/**
 * 分页响应 — 替代原版 Lombok @Data @Builder PageResponse<T>
 */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean
)