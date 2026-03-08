package com.dochibot.feature.chat.application

import com.dochibot.common.exception.CommonErrorCode
import com.dochibot.common.exception.DochiException
import com.dochibot.common.util.id.Uuid7Generator
import com.dochibot.domain.entity.ChatMessage
import com.dochibot.domain.entity.ChatSession
import com.dochibot.domain.enums.ChatRole
import com.dochibot.domain.repository.ChatSessionRepository
import com.dochibot.feature.chat.dto.ChatRequest
import com.dochibot.feature.chat.dto.ChatResponse
import com.dochibot.feature.chat.dto.ChatStreamEvent
import com.dochibot.feature.chat.exception.ChatErrorCode
import com.dochibot.common.util.log.StructuredLogSupport
import com.dochibot.feature.retrieval.application.HybridRetrievalService
import com.dochibot.feature.retrieval.infrastructure.metrics.RetrievalMetrics
import com.dochibot.feature.retrieval.application.verify.EvidenceVerifier
import com.dochibot.feature.retrieval.application.verify.QueryType
import com.dochibot.feature.retrieval.application.verify.VerifyPolicy
import com.dochibot.feature.retrieval.dto.ChunkCandidate
import com.dochibot.feature.chat.repository.ChatMessageWriter
import com.dochibot.common.config.DochibotRagProperties
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.codec.ServerSentEvent
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * RAG 기반 채팅 유즈케이스.
 *
 * - 하이브리드 리트리벌로 근거를 만들고 citations를 저장한다.
 * - 근거가 없으면 LLM을 호출하지 않고 정책 응답을 반환한다.
 *
 * @param chatClient Spring AI 챗 클라이언트
 * @param embeddingModel 임베딩 생성 모델
 * @param hybridRetrievalService 하이브리드 검색 서비스
 * @param ragProperties RAG 설정
 * @param chatSessionRepository 채팅 세션 리포지토리
 * @param chatMessageRepository 채팅 메시지 리포지토리
 * @param objectMapper JSON 직렬화
 * @param retrievalMetrics 리트리벌/검증 메트릭 기록기
 */
@Service
class ChatUseCase(
    private val chatClient: ChatClient,
    private val embeddingModel: EmbeddingModel,
    private val hybridRetrievalService: HybridRetrievalService,
    private val ragProperties: DochibotRagProperties,
    private val evidenceVerifier: EvidenceVerifier,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatMessageWriter: ChatMessageWriter,
    private val objectMapper: ObjectMapper,
    private val retrievalMetrics: RetrievalMetrics,
) {
    private val log = KotlinLogging.logger {}

    private companion object {
        const val NO_EVIDENCE_ANSWER: String = "문서에서 찾을 수 없습니다."
    }

    private data class PolicyDecision(
        val answer: String,
        val reason: String,
        val tokenCoverage: Double,
        val top1Score: Double? = null,
        val top1Top2Gap: Double? = null,
        val sameDocSupportCount: Int? = null,
        val distinctDocCount: Int? = null,
        val queryType: QueryType? = null,
        val appliedMinTokenCoverage: Double? = null,
    )

    private data class PreparedExecution(
        val session: ChatSession,
        val sessionKey: String,
        val requestMessage: String,
        val policyDecision: PolicyDecision? = null,
        val citations: List<ChatResponse.Citation> = emptyList(),
        val citationsJson: String = "[]",
        val contextMessage: String? = null,
    )

    /**
     * 사용자 질문을 SSE 스트림으로 처리한다.
     */
    fun stream(jwt: Jwt, request: ChatRequest): Flow<ServerSentEvent<ChatStreamEvent>> = flow {
        val prepared = prepareExecution(jwt = jwt, request = request)

        emit(
            sse(
                event = "metadata",
                data = ChatStreamEvent(
                    sessionId = prepared.sessionKey,
                    citations = prepared.citations,
                ),
            )
        )

        if (prepared.policyDecision != null) {
            val answer = prepared.policyDecision.answer
            persistAssistantMessage(session = prepared.session, answer = answer, citationsJson = "[]")
            if (answer.isNotBlank()) {
                emit(sse(event = "delta", data = ChatStreamEvent(delta = answer)))
            }
            emit(sse(event = "done", data = ChatStreamEvent(sessionId = prepared.sessionKey, answer = answer)))
            return@flow
        }

        val rawAnswer = StringBuilder()
        val sanitizer = ChatAnswerFormatter.StreamingAnswerSanitizer()

        try {
            chatClient.prompt()
                .system(prepared.contextMessage ?: buildContextMessage(emptyList()))
                .user(prepared.requestMessage)
                .advisors {
                    it.param(ChatMemory.CONVERSATION_ID, prepared.sessionKey)
                }
                .stream()
                .content()
                .asFlow()
                .collect { chunk ->
                    rawAnswer.append(chunk)
                    val visibleDelta = sanitizer.consume(chunk)
                    if (visibleDelta.isNotEmpty()) {
                        emit(sse(event = "delta", data = ChatStreamEvent(delta = visibleDelta)))
                    }
                }

            val tail = sanitizer.finish()
            if (tail.isNotEmpty()) {
                emit(sse(event = "delta", data = ChatStreamEvent(delta = tail)))
            }

            val answer = ChatAnswerFormatter.sanitizeAnswer(rawAnswer.toString())
                .takeIf { it.isNotBlank() }
                ?: throw DochiException(CommonErrorCode.INTERNAL_ERROR, "Empty model response after sanitizing")

            persistAssistantMessage(
                session = prepared.session,
                answer = answer,
                citationsJson = prepared.citationsJson,
            )

            emit(sse(event = "done", data = ChatStreamEvent(sessionId = prepared.sessionKey, answer = answer)))
        } catch (ex: Exception) {
            log.error(ex) { "Chat streaming failed: sessionKey=${prepared.sessionKey}" }
            emit(
                sse(
                    event = "error",
                    data = ChatStreamEvent(
                        sessionId = prepared.sessionKey,
                        message = "답변 생성 중 오류가 발생했습니다.",
                    ),
                )
            )
        }
    }

    private suspend fun prepareExecution(jwt: Jwt, request: ChatRequest): PreparedExecution {
        val userId = runCatching { UUID.fromString(jwt.subject) }
            .getOrElse {
                throw DochiException(CommonErrorCode.AUTH_INVALID_TOKEN, "Invalid subject format", it)
            }

        val sessionKey: String = request.sessionId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: Uuid7Generator.create().toString()

        val session = getOrCreateSession(ownerUserId = userId, externalSessionKey = sessionKey)

        log.info { "Chat requested: userId=$userId, sessionKey=$sessionKey, topK=${request.topK}" }

        chatMessageWriter.insert(
            ChatMessage.new(
                chatSessionId = session.id,
                role = ChatRole.USER,
                content = request.message,
            ),
        )

        val queryEmbedding = withContext(Dispatchers.IO) {
            embeddingModel.embed(listOf(request.message)).first()
        }

        val responseTopK = request.topK.coerceIn(1, ragProperties.context.topN)
        val retrievalTopK = if (ragProperties.verify.enabled) {
            maxOf(responseTopK, 2, ragProperties.verify.maxChunksToCheck)
                .coerceAtMost(ragProperties.context.topN)
        } else {
            responseTopK
        }

        val chunks = hybridRetrievalService.retrieveTopChunks(
            queryText = request.message,
            queryEmbedding = queryEmbedding,
            finalTopK = retrievalTopK,
        )

        val policyDecision = decidePolicy(queryText = request.message, chunks = chunks)
        recordVerifyMetric(policyDecision = policyDecision, chunks = chunks)
        if (policyDecision != null) {
            log.warn {
                StructuredLogSupport.toJsonLog(
                    objectMapper = objectMapper,
                    mapOf(
                        "event" to "verify_policy_routed",
                        "query_hash" to StructuredLogSupport.hashQuery(request.message),
                        "candidate_count" to chunks.size,
                        "candidate_ids_sample" to chunks.take(3).map { it.id.toString() },
                        "rerank_scores_sample" to chunks.take(5).map { it.rerankScore ?: it.rrfScore },
                        "final_chunk_ids" to emptyList<String>(),
                        "verify_result" to policyDecision.reason,
                        "policy" to ragProperties.verify.policy.name,
                        "token_coverage" to policyDecision.tokenCoverage,
                        "top1_score" to policyDecision.top1Score,
                        "top1_top2_gap" to policyDecision.top1Top2Gap,
                        "same_doc_support" to policyDecision.sameDocSupportCount,
                        "distinct_docs" to policyDecision.distinctDocCount,
                        "query_type" to policyDecision.queryType?.name,
                        "applied_min_token_coverage" to policyDecision.appliedMinTokenCoverage,
                    ),
                )
            }

            return PreparedExecution(
                session = session,
                sessionKey = sessionKey,
                requestMessage = request.message,
                policyDecision = policyDecision,
            )
        }

        val chunksForAnswer = ChatAnswerFormatter.selectEvidenceChunks(
            chunks = chunks.take(responseTopK),
            requestedCount = responseTopK,
        )

        log.info {
            StructuredLogSupport.toJsonLog(
                objectMapper = objectMapper,
                mapOf(
                    "event" to "verify_passed",
                    "query_hash" to StructuredLogSupport.hashQuery(request.message),
                    "candidate_count" to chunks.size,
                    "candidate_ids_sample" to chunks.take(3).map { it.id.toString() },
                    "rerank_scores_sample" to chunks.take(5).map { it.rerankScore ?: it.rrfScore },
                    "final_chunk_ids" to chunksForAnswer.take(5).map { it.id.toString() },
                    "verify_result" to if (ragProperties.verify.enabled) "PASS" else "SKIPPED",
                ),
            )
        }

        val citations = chunksForAnswer.map {
            ChatResponse.Citation(
                documentId = it.documentId,
                documentTitle = it.documentTitle,
                snippet = it.text.replace("\n", " ").take(300),
                page = it.page,
                section = it.sectionPath,
                score = it.finalScore,
            )
        }

        val citationsJson = withContext(Dispatchers.IO) {
            runCatching { objectMapper.writeValueAsString(citations) }
                .getOrElse { ex ->
                    log.error(ex) { "Failed to serialize citations; fallback to []" }
                    "[]"
                }
        }

        return PreparedExecution(
            session = session,
            sessionKey = sessionKey,
            requestMessage = request.message,
            citations = citations,
            citationsJson = citationsJson,
            contextMessage = buildContextMessage(chunksForAnswer),
        )
    }

    private suspend fun persistAssistantMessage(session: ChatSession, answer: String, citationsJson: String) {
        chatMessageWriter.insert(
            ChatMessage.new(
                chatSessionId = session.id,
                role = ChatRole.ASSISTANT,
                content = answer,
                citationsJson = citationsJson,
            ),
        )
    }

    private fun sse(event: String, data: ChatStreamEvent): ServerSentEvent<ChatStreamEvent> {
        return ServerSentEvent.builder<ChatStreamEvent>()
            .event(event)
            .data(data)
            .build()
    }

    /**
     * verify 단계 결정 결과를 메트릭으로 기록한다.
     *
     * @param policyDecision verify 정책 결정 결과
     * @param chunks 검색된 후보 청크
     */
    private fun recordVerifyMetric(policyDecision: PolicyDecision?, chunks: List<ChunkCandidate>) {
        val decision = when {
            chunks.isEmpty() -> "NO_CHUNKS"
            !ragProperties.verify.enabled -> "VERIFY_DISABLED"
            policyDecision != null -> "POLICY_${ragProperties.verify.policy.name}"
            else -> "PASS"
        }
        retrievalMetrics.recordVerifyDecision(decision)
    }

    private fun decidePolicy(queryText: String, chunks: List<ChunkCandidate>): PolicyDecision? {
        if (chunks.isEmpty()) {
            return PolicyDecision(
                answer = NO_EVIDENCE_ANSWER,
                reason = "NO_CHUNKS",
                tokenCoverage = 0.0,
            )
        }

        if (!ragProperties.verify.enabled) return null

        val verification = evidenceVerifier.verify(queryText = queryText, chunks = chunks)
        if (verification.isSufficient) return null

        val answer = when (ragProperties.verify.policy) {
            VerifyPolicy.NO_EVIDENCE -> NO_EVIDENCE_ANSWER
            VerifyPolicy.ASK_FOLLOWUP -> buildFollowUpAnswer(queryText)
        }

        return PolicyDecision(
            answer = answer,
            reason = verification.reason,
            tokenCoverage = verification.tokenCoverage,
            top1Score = verification.top1Score,
            top1Top2Gap = verification.top1Top2Gap,
            sameDocSupportCount = verification.sameDocSupportCount,
            distinctDocCount = verification.distinctDocCount,
            queryType = verification.queryType,
            appliedMinTokenCoverage = verification.appliedMinTokenCoverage,
        )
    }

    private fun buildFollowUpAnswer(queryText: String): String {
        val q = queryText.trim().take(120)
        return """
            정확한 근거를 찾기 어려워요.
            질문을 조금만 좁혀주면 더 잘 찾을 수 있어요: '$q'

            아래 중 1가지만 추가로 알려주세요.
            - 문서 제목/파일명(또는 링크)
            - 관련 키워드 2~3개
            - 범위(섹션/페이지/기간)
        """.trimIndent()
    }

    private fun buildContextMessage(chunks: List<ChunkCandidate>): String {
        if (chunks.isEmpty()) {
            return """
                너는 DochiBot이다.
                - 아래 제공되는 문서 근거가 없으면, 추측하지 말고 '$NO_EVIDENCE_ANSWER'라고 답한다.
                - 답변은 한국어로 간결하게 작성한다.
            """.trimIndent()
        }

        val context = chunks.withIndex().joinToString("\n\n") { (i, c) ->
            val pageInfo = c.page?.let { "p.$it" }.orEmpty()
            val sectionInfo = c.sectionPath?.takeIf { it.isNotBlank() }?.let { "- $it" }.orEmpty()
            val header = "[${i + 1}] ${c.documentTitle} $sectionInfo $pageInfo".trim()
            "$header\n${c.text.take(1200)}"
        }

        return """
            너는 DochiBot이다.
            - 아래 '문서 근거' 범위 안에서만 답한다.
            - 답변에 근거 번호를 [1], [2] 형태로 포함한다.
            - 근거가 부족하면 추측하지 말고 '$NO_EVIDENCE_ANSWER'라고 답한다.
            - 추론 과정, 사고 과정, 내부 메모를 출력하지 않는다.
            - `<think>` 같은 태그를 출력하지 말고 최종 답변만 작성한다.

            [문서 근거]
            $context
        """.trimIndent()
    }

    private suspend fun getOrCreateSession(ownerUserId: UUID, externalSessionKey: String): ChatSession {
        val existing = chatSessionRepository.findByExternalSessionKey(externalSessionKey)
        if (existing != null) {
            if (existing.ownerUserId != ownerUserId) {
                throw DochiException(ChatErrorCode.CHAT_SESSION_FORBIDDEN)
            }

            return existing
        }

        val newSession = ChatSession.new(
            externalSessionKey = externalSessionKey,
            ownerUserId = ownerUserId,
        )

        return try {
            chatSessionRepository.save(newSession)
        } catch (e: DuplicateKeyException) {
            resolveConcurrentSession(ownerUserId = ownerUserId, externalSessionKey = externalSessionKey, cause = e)
        } catch (e: DataIntegrityViolationException) {
            resolveConcurrentSession(ownerUserId = ownerUserId, externalSessionKey = externalSessionKey, cause = e)
        }
    }

    private suspend fun resolveConcurrentSession(
        ownerUserId: UUID,
        externalSessionKey: String,
        cause: Exception,
    ): ChatSession {
        val raced = chatSessionRepository.findByExternalSessionKey(externalSessionKey)
            ?: throw DochiException(CommonErrorCode.INTERNAL_ERROR, "Session created concurrently but not found", cause)

        if (raced.ownerUserId != ownerUserId) {
            throw DochiException(ChatErrorCode.CHAT_SESSION_FORBIDDEN)
        }

        return raced
    }
}
