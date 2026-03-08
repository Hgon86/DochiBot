package com.dochibot.feature.chat.controller

import com.dochibot.feature.chat.application.ChatUseCase
import com.dochibot.feature.chat.dto.ChatRequest
import com.dochibot.feature.chat.dto.ChatStreamEvent
import jakarta.validation.Valid
import kotlinx.coroutines.flow.Flow
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 채팅(RAG) API 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/chat")
class ChatController(
    private val chatUseCase: ChatUseCase,
) {
    /**
     * 사용자 질문에 대해 SSE 스트림으로 답변을 생성한다.
     *
     * @param jwt 인증 주체(JWT)
     * @param request 채팅 요청
     * @return 채팅 스트림 이벤트
     */
    @PostMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamChat(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: ChatRequest,
    ): Flow<ServerSentEvent<ChatStreamEvent>> {
        return chatUseCase.stream(jwt, request)
    }
}
