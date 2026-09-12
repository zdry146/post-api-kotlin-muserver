package com.example.postapi.model

import java.time.LocalDateTime

/**
 * Post entity — mu-server 2.4.2 + Kotlin 迁移版（替代 JPA @Entity）
 *
 * 原版用 Lombok @Data @Builder + JPA annotations，现在改用：
 * - Kotlin data class（自动 getter/setter/copy）
 * - 普通字段（（无 JPA 依赖）
 * - 手写 SQL 在 PostRepository.kt
 */
data class Post(
    var id: Long? = null,
    var title: String = "",
    var content: String = "",
    var authorName: String = "",
    var coverImage: String? = null,
    var viewCount: Int = 0,
    var likeCount: Int = 0,
    var isPublished: Boolean = false,
    var isDeleted: Boolean = false,
    var createdAt: LocalDateTime? = null,
    var updatedAt: LocalDateTime? = null
) {
    companion object {
        const val TABLE_NAME = "posts"
    }
}