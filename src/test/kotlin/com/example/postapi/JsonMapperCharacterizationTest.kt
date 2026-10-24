package com.example.postapi

import com.example.postapi.config.JsonMapper
import com.example.postapi.dto.ApiResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * JsonMapper 行为锁定测试（characterization test）
 *
 * 目的：在升级 jackson-databind 之前（CVE-2026-68497 / CVE-2026-19032 等），
 * 锁住当前序列化/反序列化的确切 JSON 输出，确保升级后逐字节一致。
 *
 * 覆盖重点：writeValueAsString —— 生产代码的响应序列化出口，升级前零直接测试。
 */
class JsonMapperCharacterizationTest {

    // ============ writeValueAsString（升级前无直接测试的出口）============

    @Test
    fun `writeValueAsString 应把 LocalDateTime 输出为 ISO 字符串而非时间戳数组`() {
        val result = JsonMapper.writeValueAsString(
            mapOf("at" to LocalDateTime.of(2026, 10, 24, 13, 45, 30))
        )
        assertThat(result).`as`("ISO-8601 字符串（WRITE_DATES_AS_TIMESTAMPS 已禁用）")
            .isEqualTo("""{"at":"2026-10-24T13:45:30"}""")
    }

    @Test
    fun `writeValueAsString 应保留 null 字段`() {
        val result = JsonMapper.writeValueAsString(mapOf("a" to null, "b" to 1))
        assertThat(result).`as`("null 不被省略").isEqualTo("""{"a":null,"b":1}""")
    }

    @Test
    fun `writeValueAsString 应输出嵌套结构且键顺序稳定`() {
        val result = JsonMapper.writeValueAsString(
            mapOf("code" to 0, "message" to "ok", "data" to mapOf("id" to 7L, "title" to "hi"))
        )
        assertThat(result).`as`("嵌套结构 + 键顺序")
            .isEqualTo("""{"code":0,"message":"ok","data":{"id":7,"title":"hi"}}""")
    }

    @Test
    fun `writeValueAsString 应正确转义引号反斜杠与换行`() {
        val result = JsonMapper.writeValueAsString(mapOf("t" to "a\"b\\c\nd"))
        assertThat(result).`as`("特殊字符转义").isEqualTo("""{"t":"a\"b\\c\nd"}""")
    }

    @Test
    fun `writeValueAsString 应序列化 ApiResult 包装结构`() {
        val result = JsonMapper.writeValueAsString(ApiResult.ok(mapOf("id" to 1L)))
        assertThat(result).`as`("ApiResult JSON")
            .isEqualTo("""{"code":200,"message":"success","data":{"id":1}}""")
    }

    // ============ readValue ============

    @Test
    fun `readValue 应解析 ApiResult 包装结构`() {
        val apiResult = JsonMapper.readValue<ApiResult<Any>>(
            """{"code":0,"message":"ok","data":{"id":1}}"""
        )
        assertThat(apiResult.code).`as`("code").isEqualTo(0)
        assertThat(apiResult.message).`as`("message").isEqualTo("ok")
    }

    @Test
    fun `readValue 应能读回 writeValueAsString 的输出`() {
        val original = mapOf("id" to 1L, "title" to "x")
        val json = JsonMapper.writeValueAsString(original)
        val back = JsonMapper.readValue<Map<String, Any>>(json)
        assertThat(back["id"]).`as`("id 往返一致").isEqualTo(1)
        assertThat(back["title"]).`as`("title 往返一致").isEqualTo("x")
    }

    @Test
    fun `readValue 遇到非法 JSON 应抛异常`() {
        assertThat(
            runCatching { JsonMapper.readValue<Map<String, Any>>("{not json") }.exceptionOrNull()
        ).`as`("非法 JSON 抛异常").isNotNull()
    }
}
