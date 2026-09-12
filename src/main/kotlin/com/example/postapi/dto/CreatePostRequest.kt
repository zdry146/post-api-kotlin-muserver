package com.example.postapi.dto

/**
 * 创建帖子请求 — 用 Kotlin init { require(...) } 替代 @NotBlank + @Size
 */
data class CreatePostRequest(
    val title: String,
    val content: String,
    val authorName: String,
    val coverImage: String?
) {
    init {
        require(title.isNotBlank()) { "标题不能为空" }
        require(title.length <= 200) { "标题长度不能超过200字符" }
        require(authorName.isNotBlank()) { "作者名称不能为空" }
        require(authorName.length <= 50) { "作者名称长度不能超过50字符" }
    }
}