package com.dochibot.feature.auth.exception

import com.dochibot.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 인증/인가 도메인의 에러 코드.
 */
enum class AuthErrorCode(
    override val code: String,
    override val status: HttpStatus
) : ErrorCode {
    INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED),
    USER_INACTIVE("AUTH_USER_INACTIVE", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID("AUTH_REFRESH_TOKEN_INVALID", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED("AUTH_REFRESH_TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED)
}
