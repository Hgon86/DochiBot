package com.dochibot.feature.chat.exception

import com.dochibot.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 채팅 도메인의 에러 코드.
 */
enum class ChatErrorCode(
    override val code: String,
    override val status: HttpStatus,
) : ErrorCode {
    CHAT_SESSION_FORBIDDEN("CHAT_SESSION_FORBIDDEN", HttpStatus.FORBIDDEN),
}
