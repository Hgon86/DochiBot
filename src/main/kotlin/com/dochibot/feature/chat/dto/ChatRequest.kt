package com.dochibot.feature.chat.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 채팅 질의 요청.
 *
 * @property message 사용자 입력
 * @property sessionId (옵션) 클라이언트가 유지하는 세션 ID
 * @property topK (옵션) RAG 컨텍스트로 사용할 청크 개수(top-k)
 */
data class ChatRequest(
    @field:NotBlank
    @field:Size(max = 4000)
    val message: String,
    @field:Size(max = 128)
    val sessionId: String? = null,
    @field:Min(1)
    @field:Max(50)
    val topK: Int = 5,
)
