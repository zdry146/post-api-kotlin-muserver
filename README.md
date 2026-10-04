# post-api-kotlin-muserver

> 帖子 CRUD API 后端 — **Spring Boot → mu-server 2.4.2 + Kotlin + Azul Java 25** 迁移版本

## 技术栈

| 维度 | 原版 (post-api) | 新版（本项目）|
|---|---|---|
| Web 框架 | Spring Boot 4.0.5 | **mu-server 2.4.2** |
| 语言 | Java 21 | **Kotlin 1.9.25** |
| JVM | OpenJDK 21 | **Azul Zulu 25** |
| 数据访问 | Spring Data JPA + Hibernate | **HikariCP + 手写 JDBC** |
| 批处理 | Spring Batch 6 | **ScheduledExecutorService + 手写 cleanup** |
| Bean Validation | Jakarta Validation | **手写 + Kotlin require/check** |
| 异常处理 | @RestControllerAdvice | **mu-server exceptionCaught** |
| 构建 | Maven | **Gradle (Kotlin DSL)** |
| 注解 | Lombok (@Data, @Builder) | **Kotlin data class** |

## API 端点（与原版一致 + 增强）

```
POST   /api/posts                          — 创建帖子
PUT    /api/posts/{id}                     — 更新帖子
GET    /api/posts/{id}                     — 获取帖子（自动 +1 viewCount）
DELETE /api/posts/{id}                     — 软删除
GET    /api/posts/published?page=&size=    — 已发布分页
GET    /api/posts/all?page=&size=          — 全部分页
GET    /api/posts/search?keyword=&page=&size=  — 标题搜索
POST   /api/posts/{id}/toggle-publish      — 切换发布状态
POST   /api/posts/{id}/like                — +1 likeCount
POST   /api/posts/{id}/unlike              — -1 likeCount（P1-2 新增）
POST   /api/admin/cleanup-job              — 手动触发清理任务

# 运维端点（P1-3, P1-4, P1-1, P3-9）
GET    /health                              — liveness（永远 200）
GET    /health/ready                        — readiness（DB check）
GET    /openapi.json                        — OpenAPI 3.1 spec（公开 CORS）
GET    /docs                                — Swagger UI
```

## 数据模型

`posts` 表（与原 schema-postgres.sql 一致）：
- `id BIGSERIAL PRIMARY KEY`
- `title VARCHAR(200) NOT NULL`
- `content TEXT NOT NULL`
- `author_name VARCHAR(50) NOT NULL`
- `cover_image VARCHAR(500)`
- `view_count BIGINT DEFAULT 0`
- `like_count BIGINT DEFAULT 0`
- `is_published BOOLEAN DEFAULT FALSE`
- `is_deleted BOOLEAN DEFAULT FALSE`
- `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`

## 启动

```bash
# 配置 PostgreSQL（参考 src/main/resources/schema-postgres.sql）
psql -U postgres -f src/main/resources/schema-postgres.sql

# 设置环境变量（应用配置）
export DB_URL="jdbc:postgresql://localhost:5432/testdb"
export DB_USER="postgres"
export DB_PASSWORD="postgres"
export HTTP_PORT="8080"

# P1-5: CORS 白名单（默认 localhost:5173 + localhost:3000）
export CORS_ALLOWED_ORIGINS="http://localhost:5173,http://localhost:3000"

# P2-6: Rate limit（默认 50/sec per-IP）
export RATE_LIMIT_PER_SECOND="50"
export RATE_LIMIT_ENABLED="true"

# P2-7: Request logging（默认 true）
export REQUEST_LOG_ENABLED="true"

# Gradle 会自动下载 Azul Zulu 25（来自foojay 仓库）
./gradlew run
```

启动后访问：
- API: http://localhost:8080/api/posts/published
- OpenAPI spec: http://localhost:8080/openapi.json
- Swagger UI: http://localhost:8080/docs
- Liveness: http://localhost:8080/health
- Readiness: http://localhost:8080/health/ready
- Admin: http://localhost:8080/admin/cleanup-job（POST 手动触发 cleanup）

## 迁移要点

1. **Web 框架**：Spring MVC 注解 → mu-server `Routes` + JAX-RS 注解（`@Path`/`@GET`/`@POST`）
2. **数据访问**：Spring Data JPA `JpaRepository` → 手写 SQL + `HikariCP` 连接池
3. **批处理**：Spring Batch `Job`/`Step`/`ItemReader`/`ItemWriter` → `ScheduledExecutorService` + 每天 0 点清理 30 天前未发布帖子（软删除）
4. **异常处理**：`@RestControllerAdvice` → mu-server `exchange.fireException()` + 自定义错误处理 handler
5. **响应封装**：`ApiResult<T>` / `PageResponse<T>` 保留（用 Kotlin `data class` 替代 Lombok `@Data @Builder`）
6. **DTO 验证**：`@NotBlank` / `@Size` → Kotlin `init { require(...) }` 块
7. **CORS**：Spring `WebMvcConfigurer` → mu-server 内置 `CORSHandler`

## 已知限制 / 待办

- 暂无 OpenTelemetry / Micrometer 监控集成
- 暂无 Docker 镜像（原版 SpringBootDocker 可参考）
- Pact contract test 未迁移（原 Java 端 `pact-tests/` 目录为空，consumer 在前端）

## 关联项目

- 原版 Spring Boot: `~/claudecode-workspace/java-projects/post-api/`
- mu-server 2.4.2 wiki: `~/my-wiki/synthesis/mu-server-2.4.2-analysis/`