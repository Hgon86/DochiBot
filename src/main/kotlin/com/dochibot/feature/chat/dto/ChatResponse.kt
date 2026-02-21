package com.dochibot.feature.chat.dto

import java.util.UUID

/**
 * 채팅 질의 응답.
 *
 * @property answer 답변(Markdown)
 * @property citations 근거 목록(Phase 1에서는 비어있을 수 있음)
 * @property sessionId 대화 세션 키
 */
data class ChatResponse(
    val answer: String,
    val citations: List<Citation> = emptyList(),
    val sessionId: String,
) {
    /**
     * AI 응답 근거.
     *
     * @property documentId 문서 ID
     * @property documentTitle 문서 제목
     * @property snippet 근거 스니펫
     * @property page (옵션) 페이지 번호
     * @property section (옵션) 섹션/조항
     * @property score (옵션) 유사도 점수
     */
    data class Citation(
        val documentId: UUID,
        val documentTitle: String,
        val snippet: String,
        val page: Int? = null,
        val section: String? = null,
        val score: Double? = null,
    )
}
