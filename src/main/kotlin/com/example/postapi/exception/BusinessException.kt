package com.example.postapi.exception

/**
 * 业务异常 — 替代原版 Lombok @Getter BusinessException
 */
open class BusinessException(
    val errorCode: Int = 400,
    override val message: String
) : RuntimeException(message)

class NotFoundException(message: String) : BusinessException(errorCode = 404, message = message)

class ValidationException(message: String) : BusinessException(errorCode = 400, message = message)