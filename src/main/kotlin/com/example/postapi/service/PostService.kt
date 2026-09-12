package com.example.postapi.service

import com.example.postapi.dto.CreatePostRequest
import com.example.postapi.dto.PageResponse
import com.example.postapi.dto.PostResponse
import com.example.postapi.dto.UpdatePostRequest
import com.example.postapi.exception.NotFoundException
import com.example.postapi.model.Post
import com.example.postapi.repository.PostRepository
import org.slf4j.LoggerFactory

/**
 * PostService — 业务逻辑（替代 Spring @Service + @Transactional PostService）
 *
 * 原版用 @Transactional 管理事务，新版每个方法内 try-with-resources 拿连接管理
 * （HikariCP 自动 commit/rollback，因为 isAutoCommit=true）
 *
 * 简单场景下不需要显式事务，因为每个 SQL 操作都是 atomic 的
 * 复杂场景可以用 TransactionTemplate 或手动 BEGIN/COMMIT（暂未实现）
 */
class PostService(private val postRepository: PostRepository) {

    private val log = LoggerFactory.getLogger(PostService::class.java)

    fun create(request: CreatePostRequest): PostResponse {
        log.info("Creating post, title={}", request.title)
        val post = Post(
            title = request.title,
            content = request.content,
            authorName = request.authorName,
            coverImage = request.coverImage,
            viewCount = 0,
            likeCount = 0,
            isPublished = false,
            isDeleted = false
        )
        val saved = postRepository.save(post)
        log.info("Post created, id={}", saved.id)
        return PostResponse.fromEntity(saved)
    }

    fun update(id: Long, request: UpdatePostRequest): PostResponse {
        log.info("Updating post, id={}", id)
        val post = findPostById(id)
        request.title?.let { post.title = it }
        request.content?.let { post.content = it }
        request.authorName?.let { post.authorName = it }
        request.coverImage?.let { post.coverImage = if (it.isEmpty()) null else it }
        request.isPublished?.let { post.isPublished = it }
        val saved = postRepository.save(post)
        log.info("Post updated, id={}", saved.id)
        return PostResponse.fromEntity(saved)
    }

    fun getById(id: Long): PostResponse {
        log.info("Getting post by id={}", id)
        val post = findPostById(id)
        postRepository.incrementViewCount(id)
        post.viewCount = post.viewCount + 1
        return PostResponse.fromEntity(post)
    }

    fun listPublished(page: Int, size: Int): PageResponse<PostResponse> {
        log.info("Listing published posts, page={}, size={}", page, size)
        val (posts, total) = postRepository.findPublished(page, size)
        return toPageResponse(posts, total, page, size)
    }

    fun listAll(page: Int, size: Int): PageResponse<PostResponse> {
        log.info("Listing all posts, page={}, size={}", page, size)
        val (posts, total) = postRepository.findAll(page, size)
        return toPageResponse(posts, total, page, size)
    }

    fun search(keyword: String, page: Int, size: Int): PageResponse<PostResponse> {
        log.info("Searching posts, keyword={}, page={}, size={}", keyword, page, size)
        val (posts, total) = postRepository.searchByTitle(keyword, page, size)
        return toPageResponse(posts, total, page, size)
    }

    fun delete(id: Long) {
        log.info("Deleting post, id={}", id)
        val post = findPostById(id)
        post.isDeleted = true
        postRepository.save(post)
        log.info("Post soft-deleted, id={}", id)
    }

    fun togglePublish(id: Long): PostResponse {
        log.info("Toggling publish status, id={}", id)
        val post = findPostById(id)
        post.isPublished = !post.isPublished
        val saved = postRepository.save(post)
        return PostResponse.fromEntity(saved)
    }

    fun like(id: Long): PostResponse {
        log.info("Liking post, id={}", id)
        val post = findPostById(id)
        postRepository.incrementLikeCount(id)
        post.likeCount = post.likeCount + 1
        return PostResponse.fromEntity(post)
    }

    private fun findPostById(id: Long): Post {
        return postRepository.findByIdAndIsDeletedFalse(id)
            ?: throw NotFoundException("帖子不存在，id=$id")
    }

    private fun toPageResponse(posts: List<Post>, total: Long, page: Int, size: Int): PageResponse<PostResponse> {
        val totalPages = if (size <= 0) 0 else ((total + size - 1) / size).toInt()
        return PageResponse(
            content = posts.map(PostResponse::fromEntity),
            page = page,
            size = size,
            totalElements = total,
            totalPages = totalPages,
            first = page == 0,
            last = page >= totalPages - 1
        )
    }
}