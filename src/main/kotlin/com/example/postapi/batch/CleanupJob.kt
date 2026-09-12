package com.example.postapi.batch

import com.example.postapi.repository.PostRepository
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * CleanupJob — 替代 Spring Batch cleanupUnpublishedPostsJob
 *
 * 原版 Spring Batch 6:
 * - ItemReader: UnpublishedPostReader
 * - ItemProcessor: softDeleteProcessor
 * - ItemWriter: softDeleteWriter
 * - @Scheduled(cron = "0 0 0 * * ?") 每天 0 点
 *
 * 新版: ScheduledExecutorService + 手写 cleanup 逻辑（30 天阈值，软删除）
 */
class CleanupJob(private val postRepository: PostRepository) {

    private val log = LoggerFactory.getLogger(CleanupJob::class.java)

    companion object {
        private const val DAYS_THRESHOLD = 30L
    }

    private var scheduler: ScheduledExecutorService? = null

    fun start() {
        val sched = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "post-api-cleanup").apply { isDaemon = true }
        }
        scheduler = sched
        val now = LocalDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
        val initialDelay = java.time.Duration.between(now, nextMidnight).toMinutes()
        sched.scheduleAtFixedRate(
            { runNow() },
            initialDelay,
            TimeUnit.DAYS.toMinutes(1),
            TimeUnit.MINUTES
        )
        log.info("CleanupJob scheduled, initialDelay={}min (next midnight)", initialDelay)
    }

    fun runNow() {
        log.info("Starting cleanup job")
        val cutoffDate = LocalDateTime.now().minusDays(DAYS_THRESHOLD)
        try {
            val posts = postRepository.findUnpublishedOlderThan(cutoffDate)
            log.info("Found {} unpublished posts older than {} days", posts.size, DAYS_THRESHOLD)
            if (posts.isEmpty()) {
                log.info("No posts to clean up")
                return
            }
            posts.chunked(100).forEachIndexed { index, chunk ->
                softDeleteBatch(chunk)
                log.info("Cleanup batch {}/{}: soft-deleted {} posts", index + 1, (posts.size + 99) / 100, chunk.size)
            }
            log.info("Cleanup job completed, total {} posts soft-deleted", posts.size)
        } catch (ex: Throwable) {
            log.error("Cleanup job failed", ex)
        }
    }

    private fun softDeleteBatch(posts: List<com.example.postapi.model.Post>) {
        posts.forEach { post ->
            post.isDeleted = true
            postRepository.save(post)
        }
    }

    fun stop() {
        scheduler?.shutdown()
        scheduler = null
    }
}