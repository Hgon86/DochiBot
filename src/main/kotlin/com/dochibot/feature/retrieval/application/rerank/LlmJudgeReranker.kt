package com.dochibot.feature.retrieval.application.rerank

import com.dochibot.common.config.DochibotRagProperties
import com.dochibot.feature.retrieval.dto.ChunkCandidate
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component
import kotlin.math.min

/**
 * LLM judge 기반 리랭커.
 *
 * - 질의와 후보 청크를 LLM에 전달하고, 후보별 근거 적합도를 0~1 점수로 받아 재정렬한다.
 * - 모델 응답이 비정상인 경우 RRF 순서(=rrfScore)로 안전하게 fallback한다.
 */
@ConditionalOnBean(ChatModel::class)
@Component
class LlmJudgeReranker(
    chatModel: ChatModel,
    private val ragProperties: DochibotRagProperties,
    private val objectMapper: ObjectMapper,
) : Reranker {
    private val log = KotlinLogging.logger {}
    private val chatClient: ChatClient = ChatClient.create(chatModel)

    override suspend fun rerank(input: RerankInput): List<RerankedChunk> {
        if (input.candidates.isEmpty()) return emptyList()

        val cfg = ragProperties.rerank.llmJudge
        val k = min(cfg.maxCandidates, input.candidates.size)
        val candidates = input.candidates.take(k)
        val tailCandidates = input.candidates.drop(k)
            .sortedByDescending { it.rrfScore }
        val fallbackScore = cfg.fallbackScore.coerceIn(0.0, 1.0)
        val scoreById = aggregateScores(
            query = input.query,
            candidates = candidates,
            snippetChars = cfg.snippetChars,
            timeoutMs = cfg.timeoutMs,
            maxAttempts = cfg.maxAttempts,
            ensembleCalls = cfg.ensembleCalls,
            fallbackScore = fallbackScore,
        ) ?: return fallbackToRrf(input.candidates)

        val scored = candidates.map { c ->
            val score = scoreById[c.id.toString()] ?: fallbackScore
            c to score
        }

        val rerankedHead = scored
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

    private fun buildSystemPrompt(): String {
        return """
            너는 RAG 시스템의 리랭킹 심사관이다.
            목표는 "질문에 답하기 위해 직접적인 근거가 되는" 후보를 위로 올리는 것이다.

            규칙:
            - 각 후보에 대해 질문과의 관련성을 0.0~1.0으로 점수화한다.
            - 후보가 질문에 대한 구체적인 사실/절차/정의를 포함하면 높은 점수.
            - 후보가 일반론/홍보/무관한 내용이면 낮은 점수.
            - 출력은 반드시 JSON 한 덩어리로만 반환한다(설명/마크다운 금지).

            출력 형식:
            {"scores":[{"id":"<candidateId>","score":0.0}]}
        """.trimIndent()
    }

    private fun buildUserPrompt(query: String, candidates: List<ChunkCandidate>, snippetChars: Int): String {
        val body = candidates.joinToString("\n\n") { c ->
            val id = c.id.toString()
            val title = c.documentTitle
            val section = c.sectionPath?.takeIf { it.isNotBlank() } ?: ""
            val snippet = centerTrim(c.text, snippetChars)
            """- id: $id
title: $title
section: $section
text: $snippet""".trimIndent()
        }

        return """
            질문:
            $query

            후보 목록:
            $body

            JSON 응답:
        """.trimIndent()
    }

    private fun fallbackToRrf(candidates: List<ChunkCandidate>): List<RerankedChunk> {
        return candidates
            .sortedByDescending { it.rrfScore }
            .mapIndexed { idx, c ->
                RerankedChunk(candidate = c, score = (c.rrfScore.coerceAtLeast(0.0)).coerceAtMost(1.0), rank = idx + 1)
            }
    }

    /**
     * 동일 입력을 여러 번 평가해 후보별 중앙값 점수를 계산한다.
     */
    private suspend fun aggregateScores(
        query: String,
        candidates: List<ChunkCandidate>,
        snippetChars: Int,
        timeoutMs: Long,
        maxAttempts: Int,
        ensembleCalls: Int,
        fallbackScore: Double,
    ): Map<String, Double>? {
        if (candidates.isEmpty()) return emptyMap()

        val calls = ensembleCalls.coerceAtLeast(1)
        val byId = candidates.associate { it.id.toString() to mutableListOf<Double>() }
        var successCount = 0

        repeat(calls) {
            val response = invokeJudgeWithRetry(
                query = query,
                candidates = candidates,
                snippetChars = snippetChars,
                timeoutMs = timeoutMs,
                maxAttempts = maxAttempts,
            )
            if (response != null) successCount++

            val responseMap = response?.scores
                ?.asSequence()
                ?.filter { !it.id.isNullOrBlank() }
                ?.associate { it.id!! to (it.score ?: fallbackScore).coerceIn(0.0, 1.0) }
                .orEmpty()

            candidates.forEach { candidate ->
                val id = candidate.id.toString()
                val value = responseMap[id] ?: fallbackScore
                byId[id]?.add(value)
            }
        }

        if (successCount == 0) {
            return null
        }

        return byId.mapValues { (_, scores) -> median(scores, fallbackScore) }
    }

    private fun median(scores: List<Double>, fallbackScore: Double): Double {
        if (scores.isEmpty()) return fallbackScore
        val sorted = scores.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }

    /**
     * LLM judge 호출과 파싱을 제한된 횟수로 재시도한다.
     *
     * @param query 사용자 질의
     * @param candidates 리랭크 대상 후보
     * @param snippetChars 후보 본문 최대 길이
     * @param timeoutMs LLM 호출 타임아웃
     * @param maxAttempts 최대 시도 횟수
     * @return 파싱 성공 시 응답, 실패 시 null
     */
    private suspend fun invokeJudgeWithRetry(
        query: String,
        candidates: List<ChunkCandidate>,
        snippetChars: Int,
        timeoutMs: Long,
        maxAttempts: Int,
    ): LlmJudgeResponse? {
        var lastError: Throwable? = null
        val attempts = maxAttempts.coerceAtLeast(1)
        val system = buildSystemPrompt()
        val user = buildUserPrompt(query = query, candidates = candidates, snippetChars = snippetChars)

        repeat(attempts) { attempt ->
            val raw = runCatching {
                withTimeout(timeoutMs) {
                    withContext(Dispatchers.IO) {
                        chatClient.prompt()
                            .system(system)
                            .user(user)
                            .call()
                            .content()
                    }
                }
            }.onFailure { ex ->
                lastError = ex
                log.warn(ex) { "LLM judge call failed: attempt=${attempt + 1}/$attempts" }
            }.getOrNull()

            if (raw.isNullOrBlank()) return@repeat

            val parsed = parseLenient(raw)
            if (parsed != null) {
                if (attempt > 0) {
                    log.info { "LLM judge rerank recovered after retry: attempt=${attempt + 1}" }
                }
                return parsed
            }
        }

        log.warn(lastError) { "LLM judge rerank parse/call failed after retries; fallback score will be used" }
        return null
    }

    /**
     * LLM 응답에서 JSON을 유연하게 파싱한다.
     *
     * - 순수 JSON
     * - 코드블록/설명 텍스트 사이의 JSON 객체
     *
     * @param raw LLM 원문 응답
     * @return 파싱 성공 시 응답 객체, 실패 시 null
     */
    private fun parseLenient(raw: String): LlmJudgeResponse? {
        val trimmed = raw.trim()
        parseStrict(trimmed)?.let { return it }

        val jsonBlocks = extractJsonObjects(trimmed)
        for (json in jsonBlocks) {
            parseStrict(json)?.let { return it }
        }

        return null
    }

    private fun parseStrict(json: String): LlmJudgeResponse? {
        return runCatching { objectMapper.readValue(json, LlmJudgeResponse::class.java) }
            .getOrNull()
    }

    /**
     * 문자열에서 균형 잡힌 JSON 객체 블록을 추출한다.
     */
    private fun extractJsonObjects(text: String): List<String> {
        val results = mutableListOf<String>()
        var depth = 0
        var start = -1

        text.forEachIndexed { idx, ch ->
            if (ch == '{') {
                if (depth == 0) start = idx
                depth++
            } else if (ch == '}') {
                if (depth > 0) {
                    depth--
                    if (depth == 0 && start >= 0) {
                        val candidate = text.substring(start, idx + 1)
                        if (candidate.contains("\"scores\"")) {
                            results += candidate
                        }
                        start = -1
                    }
                }
            }
        }

        return results
    }

    /**
     * 긴 텍스트를 중앙 기준으로 축약한다.
     */
    private fun centerTrim(text: String, maxChars: Int): String {
        val normalized = text.replace("\n", " ").trim()
        if (normalized.length <= maxChars) return normalized
        if (maxChars <= 6) return normalized.take(maxChars)

        val window = maxChars - 6
        val start = (normalized.length - window) / 2
        val middle = normalized.substring(start, start + window)
        return "...$middle..."
    }

    private data class LlmJudgeResponse(
        val scores: List<Item> = emptyList(),
    ) {
        data class Item(
            val id: String? = null,
            val score: Double? = null,
        )
    }
}
