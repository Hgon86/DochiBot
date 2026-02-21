package com.dochibot.common.exception

import org.springframework.http.HttpStatus

/**
 * 공통 에러 코드.
 */
enum class CommonErrorCode(
    override val code: String,
    override val status: HttpStatus
) : ErrorCode {
    AUTH_REQUIRED("AUTH_REQUIRED", HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_TOKEN("AUTH_INVALID_TOKEN", HttpStatus.UNAUTHORIZED),
    AUTH_FORBIDDEN("AUTH_FORBIDDEN", HttpStatus.FORBIDDEN),
    VALIDATION_ERROR("VALIDATION_ERROR", HttpStatus.BAD_REQUEST),
    NOT_FOUND("NOT_FOUND", HttpStatus.NOT_FOUND),
    CONFLICT("CONFLICT", HttpStatus.CONFLICT),
    BAD_REQUEST("BAD_REQUEST", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR)
}
