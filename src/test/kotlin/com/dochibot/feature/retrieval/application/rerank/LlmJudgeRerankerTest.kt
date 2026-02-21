package com.dochibot.feature.retrieval.application.rerank

import com.dochibot.common.config.DochibotRagProperties
import com.dochibot.feature.retrieval.dto.ChunkCandidate
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class LlmJudgeRerankerTest {
    @Test
    fun `코드블록 텍스트가 섞여도 JSON을 추출해 점수 기반 재정렬한다`() {
        val first = chunk(rrfScore = 0.9)
        val second = chunk(rrfScore = 0.7)
        val response = """
            아래는 결과입니다.
            ```json
            {"scores":[{"id":"${first.id}","score":0.1},{"id":"${second.id}","score":0.95}]}
            ```
        """.trimIndent()

        val reranker = LlmJudgeReranker(
            chatModel = FixedTextChatModel(response),
            ragProperties = props(),
            objectMapper = ObjectMapper(),
        )

        val reranked = runSuspend {
            reranker.rerank(RerankInput(query = "질문", candidates = listOf(first, second)))
        }

        assertEquals(second.id, reranked[0].candidate.id)
        assertEquals(first.id, reranked[1].candidate.id)
    }

    @Test
    fun `파싱이 실패하면 재시도 후 fallback score를 적용한다`() {
        val first = chunk(rrfScore = 0.9)
        val second = chunk(rrfScore = 0.7)
        val counter = AtomicInteger(0)

        val reranker = LlmJudgeReranker(
            chatModel = object : ChatModel {
                override fun call(prompt: Prompt): ChatResponse {
                    counter.incrementAndGet()
                    return ChatResponse(listOf(Generation(AssistantMessage("not-json"))))
                }
            },
            ragProperties = props(maxAttempts = 2, fallbackScore = 0.5),
            objectMapper = ObjectMapper(),
        )

        val reranked = runSuspend {
            reranker.rerank(RerankInput(query = "질문", candidates = listOf(first, second)))
        }

        assertEquals(2, counter.get())
        // 전면 실패 시 전체 RRF fallback
        assertEquals(0.9, reranked[0].score)
        assertEquals(0.7, reranked[1].score)
        assertEquals(first.id, reranked[0].candidate.id)
        assertEquals(second.id, reranked[1].candidate.id)
    }

    @Test
    fun `일부 후보 점수가 누락되면 fallback score를 채운다`() {
        val first = chunk(rrfScore = 0.4)
        val second = chunk(rrfScore = 0.9)
        val response = """{"scores":[{"id":"${first.id}","score":0.8}]}"""

        val reranker = LlmJudgeReranker(
            chatModel = FixedTextChatModel(response),
            ragProperties = props(maxAttempts = 2, fallbackScore = 0.5),
            objectMapper = ObjectMapper(),
        )

        val reranked = runSuspend {
            reranker.rerank(RerankInput(query = "질문", candidates = listOf(first, second)))
        }

        assertEquals(first.id, reranked[0].candidate.id)
        assertEquals(0.8, reranked[0].score)
        assertEquals(0.5, reranked[1].score)
        assertEquals(second.id, reranked[1].candidate.id)
    }

    @Test
    fun `긴 본문은 중앙 기준으로 축약되어 프롬프트에 포함된다`() {
        val longText = (1..200).joinToString(" ") { "tok$it" }
        var captured = ""
        val reranker = LlmJudgeReranker(
            chatModel = object : ChatModel {
                override fun call(prompt: Prompt): ChatResponse {
                    captured = prompt.contents
                    return ChatResponse(listOf(Generation(AssistantMessage("""{"scores":[]}"""))))
                }
            },
            ragProperties = props(snippetChars = 60),
            objectMapper = ObjectMapper(),
        )

        runSuspend {
            reranker.rerank(
                RerankInput(
                    query = "질문",
                    candidates = listOf(chunk(text = longText, rrfScore = 0.3)),
                )
            )
        }

        assertTrue(captured.contains("text: ..."))
        assertTrue(captured.contains("..."))
    }

    @Test
    fun `ensembleCalls가 3이면 후보별 중앙값 점수를 사용한다`() {
        val first = chunk(rrfScore = 0.6)
        val second = chunk(rrfScore = 0.7)
        val responses = listOf(
            """{"scores":[{"id":"${first.id}","score":0.1},{"id":"${second.id}","score":0.9}]}""",
            """{"scores":[{"id":"${first.id}","score":0.8},{"id":"${second.id}","score":0.2}]}""",
            """{"scores":[{"id":"${first.id}","score":0.7},{"id":"${second.id}","score":0.4}]}""",
        )

        val reranker = LlmJudgeReranker(
            chatModel = SequenceTextChatModel(responses),
            ragProperties = props(ensembleCalls = 3),
            objectMapper = ObjectMapper(),
        )

        val reranked = runSuspend {
            reranker.rerank(RerankInput(query = "질문", candidates = listOf(first, second)))
        }

        // first median: 0.7, second median: 0.4
        assertEquals(first.id, reranked[0].candidate.id)
        assertEquals(0.7, reranked[0].score)
        assertEquals(second.id, reranked[1].candidate.id)
        assertEquals(0.4, reranked[1].score)
    }

    @Test
    fun `judge 전면 실패 시 maxCandidates와 무관하게 전체 RRF fallback 정렬을 사용한다`() {
        val first = chunk(rrfScore = 0.2)
        val second = chunk(rrfScore = 0.9)
        val third = chunk(rrfScore = 0.7)

        val reranker = LlmJudgeReranker(
            chatModel = FixedTextChatModel("not-json"),
            ragProperties = props(maxAttempts = 1, fallbackScore = 0.5, maxCandidates = 2),
            objectMapper = ObjectMapper(),
        )

        val reranked = runSuspend {
            reranker.rerank(RerankInput(query = "질문", candidates = listOf(first, second, third)))
        }

        assertEquals(second.id, reranked[0].candidate.id)
        assertEquals(third.id, reranked[1].candidate.id)
        assertEquals(first.id, reranked[2].candidate.id)
    }

    private fun props(
        maxAttempts: Int = 2,
        fallbackScore: Double = 0.5,
        snippetChars: Int = 400,
        ensembleCalls: Int = 1,
        maxCandidates: Int = 20,
    ): DochibotRagProperties {
        return DochibotRagProperties(
            rerank = DochibotRagProperties.Rerank(
                enabled = true,
                model = RerankModel.LLM_JUDGE,
                llmJudge = DochibotRagProperties.LlmJudge(
                    maxCandidates = maxCandidates,
                    timeoutMs = 1000,
                    snippetChars = snippetChars,
                    maxAttempts = maxAttempts,
                    fallbackScore = fallbackScore,
                    ensembleCalls = ensembleCalls,
                ),
            ),
        )
    }

    private fun chunk(
        text: String = "본문",
        rrfScore: Double,
    ): ChunkCandidate {
        return ChunkCandidate(
            id = UUID.randomUUID(),
            documentId = UUID.randomUUID(),
            documentTitle = "doc",
            sectionId = UUID.randomUUID(),
            text = text,
            rrfScore = rrfScore,
            finalScore = rrfScore,
        )
    }

    private fun runSuspend(block: suspend () -> List<RerankedChunk>): List<RerankedChunk> {
        return kotlinx.coroutines.runBlocking { block() }
    }

    private class FixedTextChatModel(
        private val text: String,
    ) : ChatModel {
        override fun call(prompt: Prompt): ChatResponse {
            return ChatResponse(listOf(Generation(AssistantMessage(text))))
        }
    }

    private class SequenceTextChatModel(
        private val texts: List<String>,
    ) : ChatModel {
        private val index = AtomicInteger(0)

        override fun call(prompt: Prompt): ChatResponse {
            val current = index.getAndUpdate { i -> (i + 1).coerceAtMost(texts.lastIndex) }
            return ChatResponse(listOf(Generation(AssistantMessage(texts[current]))))
        }
    }
}
