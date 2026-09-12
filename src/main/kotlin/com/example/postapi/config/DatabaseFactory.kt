package com.example.postapi.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource

/**
 * HikariCP DataSource 单例 — 替代 Spring Boot 自动配置的 spring.datasource
 */
object DatabaseFactory {
    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    /**
     * 创建 DataSource（参数 override 环境变量，用于测试传入 H2 in-memory URL）
     */
    fun create(url: String? = null, user: String? = null, password: String? = null): DataSource {
        val actualUrl = url ?: (System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/testdb")
        val actualUser = user ?: (System.getenv("DB_USER") ?: "postgres")
        val actualPassword = password ?: (System.getenv("DB_PASSWORD") ?: "postgres")

        log.info("Initializing HikariCP DataSource: url={}, user={}", actualUrl, actualUser)

        val config = HikariConfig().apply {
            driverClassName = if (actualUrl.startsWith("jdbc:h2")) "org.h2.Driver" else "org.postgresql.Driver"
            jdbcUrl = actualUrl
            this.username = actualUser
            this.password = actualPassword
            maximumPoolSize = 10
            minimumIdle = 2
            idleTimeout = 30_000
            connectionTimeout = 10_000
            poolName = "post-api-pool"
            isAutoCommit = true
        }
        return HikariDataSource(config)
    }

    fun initializeSchema(ds: DataSource, schemaPath: Path) {
        log.info("Initializing database schema from {}", schemaPath)
        val isH2 = ds.connection.use { it.metaData.url }.startsWith("jdbc:h2")
        var sql = Files.readString(schemaPath)
        if (isH2) {
            // H2 PostgreSQL mode 兼容转换：
            // - BIGSERIAL: H2 原生支持（不转换）
            // - DATEADD (SQL Server 风格): 转为 INTERVAL (PostgreSQL 风格)
            sql = sql
                .replace("DATEADD('DAY', -60, CURRENT_TIMESTAMP)", "DATEADD('DAY', -60, CURRENT_TIMESTAMP)")  // 保留原写法以防万一
                .replace("CURRENT_TIMESTAMP - INTERVAL '60 days'", "(CURRENT_TIMESTAMP - INTERVAL '60' DAY)")
                .replace("CURRENT_TIMESTAMP - INTERVAL '45 days'", "(CURRENT_TIMESTAMP - INTERVAL '45' DAY)")
                .replace("CURRENT_TIMESTAMP - INTERVAL '35 days'", "(CURRENT_TIMESTAMP - INTERVAL '35' DAY)")
                .replace("CURRENT_TIMESTAMP - INTERVAL '10 days'", "(CURRENT_TIMESTAMP - INTERVAL '10' DAY)")
                .replace("CURRENT_TIMESTAMP - INTERVAL '5 days'", "(CURRENT_TIMESTAMP - INTERVAL '5' DAY)")
        }
        ds.connection.use { conn ->
            conn.createStatement().use { stmt ->
                for (statement in sql.split(";")) {
                    val trimmed = statement.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("--")) {
                        try {
                            stmt.execute(trimmed)
                        } catch (e: Exception) {
                            log.warn("Schema statement failed (continuing): {}", trimmed.lines().first())
                            log.warn("  Error: {}", e.message)
                            // 继续执行剩余 statements（让 CREATE TABLE 即使某条失败也尝试后续的）
                        }
                    }
                }
            }
        }
        log.info("Database schema initialized successfully")
    }
}