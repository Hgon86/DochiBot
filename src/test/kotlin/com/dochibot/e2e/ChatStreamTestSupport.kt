package com.dochibot.e2e

import com.dochibot.feature.chat.dto.ChatRequest
import com.dochibot.feature.chat.dto.ChatResponse
import com.dochibot.feature.chat.dto.ChatStreamEvent
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

data class AggregatedChatStreamResponse(
    val answer: String,
    val citations: List<ChatResponse.Citation>,
    val sessionId: String,
)

fun WebTestClient.streamChat(
    token: String,
    request: ChatRequest,
): AggregatedChatStreamResponse {
    val events = this.post()
        .uri("/api/v1/chat/stream")
        .header("Authorization", "Bearer $token")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk
        .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
        .returnResult(ChatStreamEvent::class.java)
        .responseBody
        .collectList()
        .block()
        .orEmpty()

    val answerFromDone = events.mapNotNull { event ->
        event.answer?.takeIf { it.isNotBlank() }
    }.lastOrNull()
    val streamedAnswer = events.mapNotNull { it.delta }.joinToString("")
    val answer = answerFromDone ?: streamedAnswer
    val citations = events.firstNotNullOfOrNull { event ->
        event.citations.takeIf { it.isNotEmpty() }
    }.orEmpty()
    val sessionId = events.mapNotNull { event ->
        event.sessionId?.takeIf { it.isNotBlank() }
    }.lastOrNull() ?: error("sessionId not found in chat stream")

    return AggregatedChatStreamResponse(
        answer = answer,
        citations = citations,
        sessionId = sessionId,
    )
}
