package com.example.postapi.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource

/**
 * HikariCP DataSource 单例 — 替代 Spring Boot 自动配置的 spring.datasource
 *
 * 配置从环境变量读取（DB_URL / DB_USER / DB_PASSWORD），启动时打印（密码脱敏）
 */
object DatabaseFactory {
    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun create(): DataSource {
        val url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/testdb"
        val user = System.getenv("DB_USER") ?: "postgres"
        val password = System.getenv("DB_PASSWORD") ?: "postgres"

        log.info("Initializing HikariCP DataSource: url={}, user={}", url, user)

        val config = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            jdbcUrl = url
            username = user
            password = password
            maximumPoolSize = 10
            minimumIdle = 2
            idleTimeout = 30_000
            connectionTimeout = 10_000
            poolName = "post-api-pool"
            isAutoCommit = true
        }
        return HikariDataSource(config)
    }

    /**
     * 初始化数据库 schema（执行 src/main/resources/schema-postgres.sql）
     */
    fun initializeSchema(ds: DataSource, schemaPath: Path) {
        log.info("Initializing database schema from {}", schemaPath)
        val sql = Files.readString(schemaPath)
        ds.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(sql)
            }
        }
        log.info("Database schema initialized successfully")
    }
}