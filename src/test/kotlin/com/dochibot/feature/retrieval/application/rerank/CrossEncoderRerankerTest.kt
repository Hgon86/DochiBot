package com.dochibot.feature.retrieval.application.rerank

import com.dochibot.common.config.DochibotRagProperties
import com.dochibot.feature.retrieval.dto.ChunkCandidate
import com.dochibot.feature.retrieval.mock.MockCrossEncoderServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class CrossEncoderRerankerTest {
    @Test
    fun `endpoint 점수로 head를 재정렬하고 tail은 rrf 순서를 유지한다`() = runBlocking {
        val candidates = listOf(
            chunk(rrfScore = 0.7),
            chunk(rrfScore = 0.6),
            chunk(rrfScore = 0.9),
        )

        val server = MockCrossEncoderServer { exchange ->
            val body = exchange.requestBody.bufferedReader().readText()
            val response = """
                {
                  "scores": [
                    {"id":"${candidates[0].id}","score":0.2},
                    {"id":"${candidates[1].id}","score":0.9}
                  ]
                }
            """.trimIndent()
            assertTrue(body.contains(candidates[0].id.toString()))
            assertTrue(body.contains(candidates[1].id.toString()))
            // maxCandidates=2 이므로 tail 후보는 요청 본문에 포함되지 않는다.
            assertTrue(!body.contains(candidates[2].id.toString()))
            MockCrossEncoderServer.writeJson(exchange, json = response)
        }

        server.use {
            val props = DochibotRagProperties(
                rerank = DochibotRagProperties.Rerank(
                    enabled = true,
                    model = RerankModel.CROSS_ENCODER,
                    crossEncoder = DochibotRagProperties.CrossEncoder(
                        endpoint = it.endpoint,
                        timeoutMs = 1000,
                        maxCandidates = 2,
                        snippetChars = 200,
                    ),
                ),
            )
            val reranker = CrossEncoderReranker(props, WebClient.create())

            val reranked = reranker.rerank(
                RerankInput(query = "테스트", candidates = candidates)
            )

            assertEquals(candidates[1].id, reranked[0].candidate.id)
            assertEquals(candidates[0].id, reranked[1].candidate.id)
            assertEquals(candidates[2].id, reranked[2].candidate.id)
        }
    }

    @Test
    fun `endpoint 미설정이면 rrf fallback을 사용한다`() = runBlocking {
        val candidates = listOf(
            chunk(rrfScore = 0.3),
            chunk(rrfScore = 0.8),
            chunk(rrfScore = 0.5),
        )
        val props = DochibotRagProperties(
            rerank = DochibotRagProperties.Rerank(
                enabled = true,
                model = RerankModel.CROSS_ENCODER,
                crossEncoder = DochibotRagProperties.CrossEncoder(
                    endpoint = "",
                    timeoutMs = 1000,
                    maxCandidates = 50,
                    snippetChars = 200,
                ),
            ),
        )
        val reranker = CrossEncoderReranker(props, WebClient.create())

        val reranked = reranker.rerank(RerankInput(query = "테스트", candidates = candidates))
        assertEquals(candidates[1].id, reranked[0].candidate.id)
        assertEquals(candidates[2].id, reranked[1].candidate.id)
        assertEquals(candidates[0].id, reranked[2].candidate.id)
    }

    @Test
    fun `endpoint 응답이 지연되어 타임아웃되면 rrf fallback을 사용한다`() = runBlocking {
        val candidates = listOf(
            chunk(rrfScore = 0.4),
            chunk(rrfScore = 0.9),
        )

        val server = MockCrossEncoderServer { exchange ->
            Thread.sleep(150)
            MockCrossEncoderServer.writeJson(exchange, json = """{"scores": []}""")
        }

        server.use {
            val props = DochibotRagProperties(
                rerank = DochibotRagProperties.Rerank(
                    enabled = true,
                    model = RerankModel.CROSS_ENCODER,
                    crossEncoder = DochibotRagProperties.CrossEncoder(
                        endpoint = it.endpoint,
                        timeoutMs = 10,
                        maxCandidates = 50,
                        snippetChars = 200,
                    ),
                ),
            )
            val reranker = CrossEncoderReranker(props, WebClient.create())

            val reranked = reranker.rerank(RerankInput(query = "테스트", candidates = candidates))
            assertEquals(candidates[1].id, reranked[0].candidate.id)
            assertEquals(candidates[0].id, reranked[1].candidate.id)
        }
    }

    @Test
    fun `첫 호출 실패 후 재시도 성공 시 endpoint 점수를 사용한다`() = runBlocking {
        val candidates = listOf(
            chunk(rrfScore = 0.9),
            chunk(rrfScore = 0.5),
        )
        val callCount = AtomicInteger(0)

        val server = MockCrossEncoderServer { exchange ->
            val n = callCount.incrementAndGet()
            if (n == 1) {
                MockCrossEncoderServer.writeJson(exchange, status = 500, json = """{"scores": []}""")
                return@MockCrossEncoderServer
            }
            val response = """
                {
                  "scores": [
                    {"id":"${candidates[0].id}","score":0.1},
                    {"id":"${candidates[1].id}","score":0.95}
                  ]
                }
            """.trimIndent()
            MockCrossEncoderServer.writeJson(exchange, json = response)
        }

        server.use {
            val props = DochibotRagProperties(
                rerank = DochibotRagProperties.Rerank(
                    enabled = true,
                    model = RerankModel.CROSS_ENCODER,
                    crossEncoder = DochibotRagProperties.CrossEncoder(
                        endpoint = it.endpoint,
                        timeoutMs = 1000,
                        maxCandidates = 50,
                        snippetChars = 200,
                    ),
                ),
            )
            val reranker = CrossEncoderReranker(props, WebClient.create())

            val reranked = reranker.rerank(RerankInput(query = "테스트", candidates = candidates))
            assertEquals(candidates[1].id, reranked[0].candidate.id)
            assertEquals(candidates[0].id, reranked[1].candidate.id)
            assertTrue(callCount.get() >= 2)
        }
    }

    private fun chunk(rrfScore: Double): ChunkCandidate {
        return ChunkCandidate(
            id = UUID.randomUUID(),
            documentId = UUID.randomUUID(),
            documentTitle = "doc",
            sectionId = UUID.randomUUID(),
            text = "테스트 본문",
            rrfScore = rrfScore,
            finalScore = rrfScore,
        )
    }

}
