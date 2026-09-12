package com.example.postapi.batch

import com.example.postapi.model.Post
import com.example.postapi.repository.PostRepository
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * CleanupJob — 替代 Spring Batch 的 cleanupUnpublishedPostsJob
 *
 * 原版用 Spring Batch 6：
 * - ItemReader: UnpublishedPostReader (查 30 天前未发布)
 * - ItemProcessor: softDeleteProcessor (set isDeleted=true)
 * - ItemWriter: softDeleteWriter (batch save)
 * - @Scheduled(cron = "0 0 0 * * ?") 每天 0 点执行
 *
 * 新版用 ScheduledExecutorService + 手写 cleanup 逻辑（30 天阈值，软删除）
 */
class CleanupJob(private val postRepository: PostRepository) {

    private val log = LoggerFactory.getLogger(CleanupJob::class.java)

    companion object {
        private const val DAYS_THRESHOLD = 30L
    }

    private var scheduler: ScheduledExecutorService? = null

    /**
     * 启动定时任务（每天 0 点执行）
     */
    fun start() {
        val sched = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "post-api-cleanup").apply { isDaemon = true }
        }
        scheduler = sched
        // 每天 0 点执行（用 initialDelay 计算到下一个 0 点的延迟）
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

    /**
     * 立即执行一次 cleanup（手动触发或测试）
     */
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
            // 模拟原版 chunk(100) — 简单分块（每批 100 条）
            posts.chunked(100).forEachIndexed { index, chunk ->
                softDeleteBatch(chunk)
                log.info("Cleanup batch {}/{}: soft-deleted {} posts", index + 1, (posts.size + 99) / 100, chunk.size)
            }
            log.info("Cleanup job completed, total {} posts soft-deleted", posts.size)
        } catch (ex: Throwable) {
            log.error("Cleanup job failed", ex)
        }
    }

    private fun softDeleteBatch(posts: List<Post>) {
        posts.for.forEach {
            it.isDeleted = true
            postRepository.save(it)
        }
    }

    /**
     * 关闭调度器
     */
    fun stop() {
        scheduler?.shutdown()
        scheduler = null
    }
}