package com.example.postapi.dto

/**
 * 更新帖子请求 — 用 Kotlin init { require(...) } 替代 @Size
 */
data class UpdatePostRequest(
    val title: String? = null,
    val content: String? = null,
    val authorName: String? = null,
    val coverImage: String? = null,
    val isPublished: Boolean? = null
) {
    init {
        if (title != null) require(title.length <= 200) { "标题长度不能超过200字符" }
        if (authorName != null) require(authorName.length <= 50) { "作者名称长度不能超过50字符" }
    }
}