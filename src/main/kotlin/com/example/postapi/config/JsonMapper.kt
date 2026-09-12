package com.example.postapi.config

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * 单例 ObjectMapper（Kotlin version of Jackson）
 * 注册 JavaTimeModule 支持 javaLocalDateTime 序列化
 */
object JsonMapper {
    val mapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    fun writeValueAsString(value: Any): String = mapper.writeValueAsString(value)
    fun <T> readValue(json: String, clazz: Class<T>): T = mapper.readValue(json, clazz)
    inline fun <reified T> readValue(json: String): T = mapper.readValue(json, T::class.java)
}