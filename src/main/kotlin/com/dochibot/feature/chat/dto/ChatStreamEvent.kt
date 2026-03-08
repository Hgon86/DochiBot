package com.dochibot.feature.chat.dto

/**
 * SSE 기반 채팅 스트림 이벤트 payload.
 *
 * @property sessionId 대화 세션 키
 * @property delta 증분 답변 텍스트
 * @property answer 최종 확정 답변
 * @property citations 근거 목록
 * @property message 오류/안내 메시지
 */
data class ChatStreamEvent(
    val sessionId: String? = null,
    val delta: String? = null,
    val answer: String? = null,
    val citations: List<ChatResponse.Citation> = emptyList(),
    val message: String? = null,
)
