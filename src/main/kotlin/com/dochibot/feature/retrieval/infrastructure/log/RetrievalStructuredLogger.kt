package com.dochibot.feature.retrieval.infrastructure.log

import com.dochibot.common.util.log.StructuredLogSupport
import com.dochibot.feature.retrieval.dto.ChunkCandidate
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 리트리벌 결과를 구조화 로그(JSON)로 기록한다.
 *
 * @property objectMapper 로그 직렬화기
 */
@Component
class RetrievalStructuredLogger(
    private val objectMapper: ObjectMapper,
) {
    private val log = KotlinLogging.logger {}

    /**
     * 리트리벌 처리 결과를 INFO 레벨로 기록한다.
     *
     * @param queryText 원본 질의 텍스트
     * @param fused RRF 융합 후보 목록
     * @param result 최종 반환 후보 목록
     * @param top1FinalScore 최종 top1 점수
     * @param tookMs 처리 시간(ms)
     */
    fun logRetrievalResult(
        queryText: String,
        fused: List<ChunkCandidate>,
        result: List<ChunkCandidate>,
        top1FinalScore: Double?,
        tookMs: Long,
    ) {
        if (!log.isInfoEnabled()) return

        val event = mapOf(
            "event" to "retrieval_result",
            "query_hash" to StructuredLogSupport.hashQuery(queryText),
            "candidate_count" to fused.size,
            "candidate_ids_sample" to fused.take(3).map { it.id.toString() },
            "rerank_scores_sample" to result.take(5).map { it.rerankScore ?: it.rrfScore },
            "final_chunk_ids" to result.take(5).map { it.id.toString() },
            "verify_result" to null,
            "top1_final_score" to top1FinalScore,
            "took_ms" to tookMs,
        )

        log.info {
            StructuredLogSupport.toJsonLog(objectMapper = objectMapper, payload = event)
        }
    }
}
