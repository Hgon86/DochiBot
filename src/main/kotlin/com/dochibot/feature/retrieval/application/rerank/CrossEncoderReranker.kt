package com.dochibot.feature.retrieval.application.rerank

import com.dochibot.common.config.DochibotRagProperties
import com.dochibot.feature.retrieval.dto.ChunkCandidate
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import kotlin.math.min

/**
 * Cross-Encoder 리랭커(외부 스코어링 엔드포인트 연동).
 *
 * - 실제 Cross-Encoder 모델은 보통 별도 서빙이 필요하므로, HTTP endpoint에 질의/후보를 전달해 점수를 받아온다.
 * - endpoint 미설정/오류/타임아웃이면 RRF 순서로 안전하게 fallback한다.
 */
@Component
class CrossEncoderReranker(
    private val ragProperties: DochibotRagProperties,
    private val webClient: WebClient = WebClient.create(),
) : Reranker {
    private val log = KotlinLogging.logger {}
    private companion object {
        const val MAX_ATTEMPTS: Int = 3
        const val RETRY_BASE_DELAY_MS: Long = 50
    }

    override suspend fun rerank(input: RerankInput): List<RerankedChunk> {
        if (input.candidates.isEmpty()) return emptyList()

        val cfg = ragProperties.rerank.crossEncoder
        val endpoint = cfg.endpoint.trim()
        if (endpoint.isBlank()) {
            return fallbackToRrf(input.candidates)
        }

        val k = min(cfg.maxCandidates, input.candidates.size)
        val candidates = input.candidates.take(k)
        val tailCandidates = input.candidates.drop(k)
            .sortedByDescending { it.rrfScore }
        val request = CrossEncoderRerankRequest(
            query = input.query,
            candidates = candidates.map { it.toRequestCandidate(cfg.snippetChars) },
        )

        val response = requestWithRetry(endpoint = endpoint, request = request, timeoutMs = cfg.timeoutMs)
            ?: return fallbackToRrf(input.candidates)

        val scoreById = response.scores
            .asSequence()
            .filter { !it.id.isNullOrBlank() }
            .associate { it.id!! to (it.score ?: 0.0) }

        val rerankedHead = candidates
            .map { c -> c to (scoreById[c.id.toString()] ?: 0.0) }
            .sortedWith(compareByDescending<Pair<ChunkCandidate, Double>> { it.second }
                .thenByDescending { it.first.rrfScore })
            .map { (candidate, score) ->
                RerankedChunk(candidate = candidate, score = score.coerceIn(0.0, 1.0), rank = 0)
            }

        val rerankedTail = tailCandidates.map { candidate ->
            RerankedChunk(
                candidate = candidate,
                score = candidate.rrfScore.coerceIn(0.0, 1.0),
                rank = 0,
            )
        }

        return (rerankedHead + rerankedTail)
            .mapIndexed { idx, reranked -> reranked.copy(rank = idx + 1) }
    }

    /**
     * 외부 Cross-Encoder endpoint를 제한된 횟수로 재시도한다.
     *
     * @param endpoint Cross-Encoder endpoint URL
     * @param request 요청 바디
     * @param timeoutMs 호출 타임아웃(ms)
     * @return 성공 시 응답, 실패 시 null
     */
    private suspend fun requestWithRetry(
        endpoint: String,
        request: CrossEncoderRerankRequest,
        timeoutMs: Long,
    ): CrossEncoderRerankResponse? {
        val apiKey = ragProperties.rerank.crossEncoder.apiKey.trim()
        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            val response = runCatching {
                withTimeout(timeoutMs) {
                    val spec = webClient.post()
                        .uri(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                    if (apiKey.isNotBlank()) {
                        spec.header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                        spec.header("X-API-Key", apiKey)
                    }
                    spec.bodyValue(request)
                        .retrieve()
                        .awaitBody<CrossEncoderRerankResponse>()
                }
            }.onFailure { ex ->
                lastError = ex
            }.getOrNull()

            if (response != null) {
                if (attempt > 0) {
                    log.info { "Cross-encoder rerank recovered after retry: attempt=${attempt + 1}" }
                }
                return response
            }

            if (attempt < MAX_ATTEMPTS - 1) {
                delay(RETRY_BASE_DELAY_MS * (attempt + 1))
            }
        }

        log.warn(lastError) { "Cross-encoder rerank failed after retries; fallback to RRF ordering" }
        return null
    }

    private fun ChunkCandidate.toRequestCandidate(snippetChars: Int): CrossEncoderRerankRequest.Candidate {
        return CrossEncoderRerankRequest.Candidate(
            id = id.toString(),
            documentTitle = documentTitle,
            sectionPath = sectionPath,
            text = text.replace("\n", " ").take(snippetChars),
        )
    }

    private fun fallbackToRrf(candidates: List<ChunkCandidate>): List<RerankedChunk> {
        return candidates
            .sortedByDescending { it.rrfScore }
            .mapIndexed { idx, c ->
                RerankedChunk(candidate = c, score = (c.rrfScore.coerceAtLeast(0.0)).coerceAtMost(1.0), rank = idx + 1)
            }
    }

    private data class CrossEncoderRerankRequest(
        val query: String,
        val candidates: List<Candidate>,
    ) {
        data class Candidate(
            val id: String,
            val documentTitle: String,
            val sectionPath: String? = null,
            val text: String,
        )
    }

    private data class CrossEncoderRerankResponse(
        val scores: List<Item> = emptyList(),
    ) {
        data class Item(
            val id: String? = null,
            val score: Double? = null,
        )
    }
}
