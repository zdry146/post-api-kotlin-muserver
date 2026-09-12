package com.example.postapi.dto

import com.example.postapi.model.Post
import java.time.LocalDateTime

/**
 * Post 响应 DTO — 替代原版 Lombok @Data @Builder PostResponse
 */
data class PostResponse(
    val id: Long?,
    val title: String,
    val content: String,
    val authorName: String,
    val coverImage: String?,
    val viewCount: Int,
    val likeCount: Int,
    val isPublished: Boolean,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun fromEntity(post: Post): PostResponse = PostResponse(
            id = post.id,
            title = post.title,
            content = post.content,
            authorName = post.authorName,
            coverImage = post.coverImage,
            viewCount = post.viewCount,
            likeCount = post.likeCount,
            isPublished = post.isPublished,
            createdAt = post.createdAt,
            updatedAt = post.updatedAt
        )
    }
}