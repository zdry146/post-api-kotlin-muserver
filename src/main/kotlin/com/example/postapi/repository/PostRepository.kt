package com.example.postapi.repository

import com.example.postapi.model.Post
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.time.LocalDateTime
import javax.sql.DataSource

/**
 * 手写 JDBC Post Repository — 替代 Spring Data JPA PostRepository
 *
 * 原版 JPA 接口有 7 个方法（save / findById / findByIdAndIsDeletedFalse / list 等），
 * 现在全部用 SQL + PreparedStatement 实现
 */
class PostRepository(private val ds: DataSource) {

    companion object {
        private const val COLS = "id, title, content, author_name, cover_image, " +
            "view_count, like_count, is_published, is_deleted, created_at, updated_at"
    }

    // ============ 创建/更新 ============

    fun save(post: Post): Post {
        return if (post.id == null) insert(post) else update(post)
    }

    private fun insert(post: Post): Post {
        val sql = """
            INSERT INTO posts (title, content, author_name, cover_image, view_count, like_count, is_published, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        ds.connection.use { conn ->
            conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
                ps.setString(1, post.title)
                ps.setString(2, post.content)
                ps.setString(3, post.authorName)
                ps.setString(4, post.coverImage)
                ps.setInt(5, post.viewCount)
                ps.setInt(6, post.likeCount)
                ps.setBoolean(7, post.isPublished)
                ps.setBoolean(8, post.isDeleted)
                ps.executeUpdate()
                ps.generatedKeys.use { keys ->
                    if (keys.next()) {
                        val id = keys.getLong(1)
                        // SELECT 全 row 拿 createdAt/updatedAt（H2 不支持 RETURNING，兜底用额外 SELECT）
                        return findByIdRaw(conn, id) ?: post.copy(id = id)
                    }
                    return post
                }
            }
        }
    }

    private fun update(post: Post): Post {
        val postId = post.id ?: return post
        val sql = """
            UPDATE posts SET title=?, content=?, author_name=?, cover_image=?, view_count=?, like_count=?,
                  is_published=?, is_deleted=?, updated_at=CURRENT_TIMESTAMP
            WHERE id=?
        """.trimIndent()
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, post.title)
                ps.setString(2, post.content)
                ps.setString(3, post.authorName)
                ps.setString(4, post.coverImage)
                ps.setInt(5, post.viewCount)
                ps.setInt(6, post.likeCount)
                ps.setBoolean(7, post.isPublished)
                ps.setBoolean(8, post.isDeleted)
                ps.setLong(9, postId)
                val rows = ps.executeUpdate()
                if (rows > 0) {
                    return findByIdRaw(conn, postId) ?: post
                }
                return post
            }
        }
    }

    /**
     * 拿指定 id 的全 row（不 filter is_deleted）— 用于 insert/update 后拿 DB 默认值
     * （created_at / updated_at 由 DB DEFAULT CURRENT_TIMESTAMP 生成）
     */
    private fun findByIdRaw(conn: java.sql.Connection, id: Long): Post? {
        val sql = "SELECT $COLS FROM posts WHERE id = ?"
        conn.prepareStatement(sql).use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs ->
                return if (rs.next()) mapRow(rs) else null
            }
        }
    }

    // ============ 查询 ============

    fun findByIdAndIsDeletedFalse(id: Long): Post? {
        val sql = "SELECT $COLS FROM posts WHERE id = ? AND is_deleted = false"
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs ->
                    return if (rs.next()) mapRow(rs) else null
                }
            }
        }
    }

    /**
     * 已发布列表（按 createdAt 倒序）
     */
    fun findPublished(page: Int, size: Int): Pair<List<Post>, Long> {
        return findPaged(
            whereClause = "WHERE is_published = true AND is_deleted = false",
            orderBy = "ORDER BY created_at DESC",
            page = page,
            size = size
        )
    }

    /**
     * 所有非删除帖子（按 createdAt 倒序）
     */
    fun findAll(page: Int, size: Int): Pair<List<Post>, Long> {
        return findPaged(
            whereClause = "WHERE is_deleted = false",
            orderBy = "ORDER BY created_at DESC",
            page = page,
            size = size
        )
    }

    /**
     * 按标题搜索（大小写不敏感，LIKE）
     */
    fun searchByTitle(keyword: String, page: Int, size: Int): Pair<List<Post>, Long> {
        return findPaged(
            whereClause = "WHERE is_deleted = false AND LOWER(title) LIKE LOWER(?)",
            orderBy = "ORDER BY created_at DESC",
            params = listOf("%$keyword%"),
            page = page,
            size = size
        )
    }

    /**
     * 通用的分页查询 + 总数统计
     */
    private fun findPaged(
        whereClause: String,
        orderBy: String,
        params: List<Any> = emptyList(),
        page: Int,
        size: Int
    ): Pair<List<Post>, Long> {
        val offset = page * size
        val sql = "SELECT $COLS FROM posts $whereClause $orderBy LIMIT ? OFFSET ?"
        val countSql = "SELECT COUNT(*) FROM posts $whereClause"

        val posts = mutableListOf<Post>()
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                var i = 1
                params.forEach {
                    ps.setObject(i++, it)
                }
                ps.setInt(i++, size)
                ps.setInt(i, offset)
                ps.executeQuery().use { rs ->
                    while (rs.next()) posts.add(mapRow(rs))
                }
            }
            conn.prepareStatement(countSql).use { ps ->
                var i = 1
                params.forEach {
                    ps.setObject(i++, it)
                }
                ps.executeQuery().use { rs ->
                    if (rs.next()) return posts to rs.getLong(1)
                }
            }
        }
        return posts to 0L
    }

    // ============ 批量 ============

    fun incrementViewCount(id: Long): Int {
        val sql = "UPDATE posts SET view_count = view_count + 1 WHERE id = ? AND is_deleted = false"
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, id)
                return ps.executeUpdate()
            }
        }
    }

    fun incrementLikeCount(id: Long): Int {
        val sql = "UPDATE posts SET like_count = like_count + 1 WHERE id = ? AND is_deleted = false"
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, id)
                return ps.executeUpdate()
            }
        }
    }

    /**
     * Decrement like count but never below 0 (GREATEST guards against negative).
     * Returns the number of rows affected (0 if post not found / already deleted).
     */
    fun decrementLikeCount(id: Long): Int {
        val sql = "UPDATE posts SET like_count = GREATEST(like_count - 1, 0) WHERE id = ? AND is_deleted = false"
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, id)
                return ps.executeUpdate()
            }
        }
    }

    /**
     * 查找 N 天前未发布且未删除的帖子（用于 batch cleanup）
     */
    fun findUnpublishedOlderThan(cutoffDate: LocalDateTime): List<Post> {
        val sql = """
            SELECT $COLS FROM posts
            WHERE is_published = false AND is_deleted = false AND created_at < ?
        """.trimIndent()
        val posts = mutableListOf<Post>()
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setTimestamp(1, Timestamp.valueOf(cutoffDate))
                ps.executeQuery().use { rs ->
                    while (rs.next()) posts.add(mapRow(rs))
                }
            }
        }
        return posts
    }

    // ============ 行映射 ============

    private fun mapRow(rs: ResultSet): Post = Post(
        id = rs.getLong("id"),
        title = rs.getString("title"),
        content = rs.getString("content") ?: "",
        authorName = rs.getString("author_name"),
        coverImage = rs.getString("cover_image"),
        viewCount = rs.getInt("view_count"),
        likeCount = rs.getInt("like_count"),
        isPublished = rs.getBoolean("is_published"),
        isDeleted = rs.getBoolean("is_deleted"),
        createdAt = rs.getTimestamp("created_at")?.toLocalDateTime(),
        updatedAt = rs.getTimestamp("updated_at")?.toLocalDateTime()
    )
}