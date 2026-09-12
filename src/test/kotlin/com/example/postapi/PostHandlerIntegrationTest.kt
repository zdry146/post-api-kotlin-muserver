package com.example.postapi

import com.example.postapi.batch.CleanupJob
import com.example.postapi.config.DatabaseFactory
import com.example.postapi.config.JsonMapper
import com.example.postapi.dto.ApiResult
import com.example.postapi.dto.PageResponse
import com.example.postapi.dto.PostResponse
import com.example.postapi.handler.AdminHandler
import com.example.postapi.handler.PostHandler
import com.example.postapi.repository.PostRepository
import com.example.postapi.service.PostService
import io.muserver.Method
import io.muserver.MuServer
import io.muserver.MuServerBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Paths
import java.time.Duration
import javax.sql.DataSource

/**
 * PostHandler 集成测试 — 替代 Spring MockMvc PostControllerTest
 *
 * 策略：用 java.net.http.HttpClient 启动真实 mu-server + H2 in-memory DB
 * 覆盖原版 PostControllerTest 9 个 endpoint 的行为（CRUD + 分页 + 搜索 + toggle + like）
 *
 * 优势（vs Spring MockMvc）：
 * - 真实 HTTP（验证 mu-server Routes DSL + JsonMapper + DatabaseFactory 完整链路）
 * - 无 mock 框架（除 service 单元测试用 mockk）
 */
class PostHandlerIntegrationTest {

    private lateinit var server: MuServer
    private lateinit var dataSource: DataSource
    private lateinit var client: HttpClient
    private lateinit var baseUri: String

    @BeforeEach
    fun setUp() {
        // 1. H2 in-memory database（test 隔离）
        val h2Url = "jdbc:h2:mem:test-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        dataSource = DatabaseFactory.create(url = h2Url, user = "sa", password = "")

        DatabaseFactory.initializeSchema(dataSource, Paths.get("src/main/resources/schema-postgres.sql"))

        // 2. Wire up Repository / Service / Handler
        val postRepository = PostRepository(dataSource)
        val postService = PostService(postRepository)
        val postHandler = PostHandler(postService)
        val cleanupJob = CleanupJob(postRepository)
        val adminHandler = AdminHandler(cleanupJob)

        // 3. 启动 mu-server（端口 0 = random）
        val builder = MuServerBuilder.httpServer()
            .withHttpPort(0)
        postHandler.register().forEach { builder.addHandler(it) }
        adminHandler.register().forEach { builder.addHandler(it) }
        server = builder.start()
        val port = server.uri().port
        baseUri = "http://localhost:$port"

        // 4. HttpClient
        client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()
    }

    @AfterEach
    fun tearDown() {
        server.stop()
        (dataSource as? com.zaxxer.hikari.HikariDataSource)?.close()
    }

    // ============ Helper ============

    private fun get(path: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder().uri(URI.create("$baseUri$path")).GET().build(),
        HttpResponse.BodyHandlers.ofString()
    )

    private fun post(path: String, body: String? = null): HttpResponse<String> = client.send(
        HttpRequest.newBuilder()
            .uri(URI.create("$baseUri$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body ?: ""))
            .build(),
        HttpResponse.BodyHandlers.ofString()
    )

    private fun put(path: String, body: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder()
            .uri(URI.create("$baseUri$path"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString()
    )

    private fun delete(path: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder().uri(URI.create("$baseUri$path")).DELETE().build(),
        HttpResponse.BodyHandlers.ofString()
    )

    // ============ 9 个 Endpoint 测试（覆盖原版 PostControllerTest 全部场景）============

    @Test
    fun `createPost - POST api posts`() {
        val resp = post(
            "/api/posts",
            """{"title":"New Post","content":"Content","authorName":"Author"}"""
        )
        assertThat(resp.statusCode()).isEqualTo(200)
        val result = JsonMapper.readValue<ApiResult<PostResponse>>(resp.body())
        assertThat(result.code).isEqualTo(200)
        assertThat(result.data?.title).isEqualTo("New Post")
        assertThat(result.data?.id).isNotNull()
    }

    @Test
    fun `updatePost - PUT api posts id`() {
        // 先创建
        val created = post("/api/posts", """{"title":"Original","content":"X","authorName":"A"}""")
        val createdId = JsonMapper.readValue<ApiResult<PostResponse>>(created.body()).data?.id!!

        // 更新
        val resp = put(
            "/api/posts/$createdId",
            """{"title":"Updated Title","content":"Updated"}"""
        )
        assertThat(resp.statusCode()).isEqualTo(200)
        val result = JsonMapper.readValue<ApiResult<PostResponse>>(resp.body())
        assertThat(result.data?.title).isEqualTo("Updated Title")
    }

    @Test
    fun `getPostById - GET api posts id`() {
        val created = post("/api/posts", """{"title":"Test","content":"X","authorName":"A"}""")
        val id = JsonMapper.readValue<ApiResult<PostResponse>>(created.body()).data?.id!!

        val resp = get("/api/posts/$id")
        assertThat(resp.statusCode()).isEqualTo(200)
        val result = JsonMapper.readValue<ApiResult<PostResponse>>(resp.body())
        assertThat(result.data?.id).isEqualTo(id)
        assertThat(result.data?.title).isEqualTo("Test")
    }

    @Test
    fun `getPostNotFound - 404 when id missing`() {
        val resp = get("/api/posts/999999")
        assertThat(resp.statusCode()).isEqualTo(404)
        val result = JsonMapper.readValue<ApiResult<Any>>(resp.body())
        assertThat(result.code).isEqualTo(404)
    }

    @Test
    fun `deletePost - DELETE api posts id`() {
        val created = post("/api/posts", """{"title":"ToDelete","content":"X","authorName":"A"}""")
        val id = JsonMapper.readValue<ApiResult<PostResponse>>(created.body()).data?.id!!

        val resp = delete("/api/posts/$id")
        assertThat(resp.statusCode()).isEqualTo(200)

        // 再次 GET 应返回 404
        val afterDelete = get("/api/posts/$id")
        assertThat(afterDelete.statusCode()).isEqualTo(404)
    }

    @Test
    fun `listPublishedPosts - GET api posts published`() {
        // 创建一个 published 帖子
        val created = post("/api/posts", """{"title":"Pub","content":"X","authorName":"A"}""")
        val createdId = JsonMapper.readValue<ApiResult<PostResponse>>(created.body()).data?.id!!
        put("/api/posts/$createdId", """{"isPublished":true}""")  // 发布

        val resp = get("/api/posts/published?page=0&size=10")
        assertThat(resp.statusCode()).isEqualTo(200)
        val result = JsonMapper.readValue<ApiResult<PageResponse<PostResponse>>>(resp.body())
        assertThat(result.data?.totalElements).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `listAllPosts - GET api posts all`() {
        post("/api/posts", """{"title":"All1","content":"X","authorName":"A"}""")
        post("/api/posts", """{"title":"All2","content":"X","authorName":"A"}""")

        val resp = get("/api/posts/all?page=0&size=10")
        assertThat(resp.statusCode()).isEqualTo(200)
        val result = JsonMapper.readValue<ApiResult<PageResponse<PostResponse>>>(resp.body())
        assertThat(result.data?.totalElements).isGreaterThanOrEqualTo(2)
    }

    @Test
    fun `searchPosts - GET api posts search`() {
        post("/api/posts", """{"title":"SearchablePost","content":"X","authorName":"A"}""")
        post("/api/posts", """{"title":"OtherPost","content":"X","authorName":"A"}""")

        val resp = get("/api/posts/search?keyword=Search&page=0&size=10")
        assertThat(resp.statusCode()).isEqualTo(200)
        val result = JsonMapper.readValue<ApiResult<PageResponse<PostResponse>>>(resp.body())
        assertThat(result.data?.content?.firstOrNull()?.title).isEqualTo("SearchablePost")
    }

    @Test
    fun `togglePublish - POST api posts id toggle-publish`() {
        val created = post("/api/posts", """{"title":"Toggle","content":"X","authorName":"A"}""")
        val createdId = JsonMapper.readValue<ApiResult<PostResponse>>(created.body()).data?.id!!

        val resp = post("/api/posts/$createdId/toggle-publish")
        assertThat(resp.statusCode()).isEqualTo(200)
        val result = JsonMapper.readValue<ApiResult<PostResponse>>(resp.body())
        assertThat(result.data?.isPublished).isTrue

        // 再次 toggle 应回 false
        val resp2 = post("/api/posts/$createdId/toggle-publish")
        val result2 = JsonMapper.readValue<ApiResult<PostResponse>>(resp2.body())
        assertThat(result2.data?.isPublished).isFalse
    }

    @Test
    fun `likePost - POST api posts id like`() {
        val created = post("/api/posts", """{"title":"Like","content":"X","authorName":"A"}""")
        val createdId = JsonMapper.readValue<ApiResult<PostResponse>>(created.body()).data?.id!!

        val resp = post("/api/posts/$createdId/like")
        assertThat(resp.statusCode()).isEqualTo(200)
        val result = JsonMapper.readValue<ApiResult<PostResponse>>(resp.body())
        assertThat(result.data?.likeCount).isEqualTo(1)
    }

    // ============ Batch Job 测试 ============

    @Test
    fun `cleanupJob - POST api admin cleanup-job triggers runNow`() {
        // 创建一些帖子
        post("/api/posts", """{"title":"Test","content":"X","authorName":"A"}""")

        val resp = post("/api/admin/cleanup-job")
        assertThat(resp.statusCode()).isEqualTo(200)
    }

    // ============ 辅助 DTO/Exception 单元测试（替代原版 5 个单元 test）============

    @Test
    fun `ApiResult ok - factory method`() {
        val result = ApiResult.ok("hello")
        assertThat(result.code).isEqualTo(200)
        assertThat(result.message).isEqualTo("success")
        assertThat(result.data).isEqualTo("hello")
    }

    @Test
    fun `ApiResult fail - factory method`() {
        val result = ApiResult.fail<Int>(400, "bad request")
        assertThat(result.code).isEqualTo(400)
        assertThat(result.message).isEqualTo("bad request")
        assertThat(result.data).isNull()
    }

    @Test
    fun `CreatePostRequest init - validates required fields`() {
        // 标题为空应抛 IllegalArgumentException
        var threw = false
        try {
            com.example.postapi.dto.CreatePostRequest("", "c", "a", null)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertThat(threw).isTrue
    }

    @Test
    fun `CreatePostRequest init - title length max 200`() {
        var threw = false
        val longTitle = "a".repeat(201)
        try {
            com.example.postapi.dto.CreatePostRequest(longTitle, "c", "a", null)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertThat(threw).isTrue
    }

    @Test
    fun `NotFoundException - 404 code`() {
        val ex = com.example.postapi.exception.NotFoundException("not here")
        assertThat(ex.errorCode).isEqualTo(404)
        assertThat(ex.message).isEqualTo("not here")
    }
}