package com.dochibot.feature.retrieval.eval

import com.dochibot.feature.retrieval.dto.ChunkCandidate
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.UUID

/**
 * Phase 2 평가셋 루트 모델.
 *
 * @property version 평가셋 스키마 버전
 * @property items 평가 항목 목록
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Phase2EvalSet(
    val version: Int = 1,
    val items: List<Phase2EvalItem> = emptyList(),
)

/**
 * Phase 2 단일 평가 항목.
 *
 * @property id 항목 식별자
 * @property query 사용자 질의
 * @property expected 기대 근거 조건
 * @property queryType 질의 유형(선택)
 * @property tags 태그(선택)
 * @property difficulty 난이도(선택)
 * @property notes 비고(선택)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Phase2EvalItem(
    val id: String,
    val query: String,
    val expected: Phase2EvalExpected? = null,
    val queryType: String? = null,
    val tags: List<String> = emptyList(),
    val difficulty: String? = null,
    val notes: String? = null,
)

/**
 * 기대 근거 조건.
 *
 * @property chunkIds 기대 청크 ID 목록(있으면 최우선 사용)
 * @property documentId 기대 문서 ID
 * @property documentTitleContains 기대 문서 제목 부분 문자열
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Phase2EvalExpected(
    val chunkIds: List<UUID> = emptyList(),
    val documentId: UUID? = null,
    val documentTitleContains: String? = null,
)

/**
 * 평가셋 스키마/필수값을 검증한다.
 */
object Phase2EvalValidator {
    /**
     * @param evalSet 검증 대상 평가셋
     * @return 오류 메시지 목록(비어 있으면 유효)
     */
    fun validate(evalSet: Phase2EvalSet): List<String> {
        val errors = mutableListOf<String>()
        if (evalSet.items.isEmpty()) {
            errors += "items must not be empty"
            return errors
        }

        val duplicatedIds = evalSet.items
            .groupBy { it.id.trim() }
            .filter { it.key.isNotBlank() && it.value.size > 1 }
            .keys
        if (duplicatedIds.isNotEmpty()) {
            errors += "duplicated item ids: ${duplicatedIds.joinToString(",")}" 
        }

        evalSet.items.forEachIndexed { index, item ->
            val path = "items[$index]"
            if (item.id.isBlank()) {
                errors += "$path.id must not be blank"
            }
            if (item.query.isBlank()) {
                errors += "$path.query must not be blank"
            }

            val expected = item.expected
            if (expected == null) {
                errors += "$path.expected must not be null"
            } else {
                val hasChunkIds = expected.chunkIds.isNotEmpty()
                val hasDocId = expected.documentId != null
                val hasTitle = !expected.documentTitleContains.isNullOrBlank()
                if (!hasChunkIds && !hasDocId && !hasTitle) {
                    errors += "$path.expected must include one of chunkIds/documentId/documentTitleContains"
                }
            }
        }

        return errors
    }
}

/**
 * 단일 항목의 랭크 기반 평가 결과.
 *
 * @property rank 기대 근거의 최초 순위(없으면 null)
 * @property hit1 Hit@1
 * @property hit3 Hit@3
 * @property hit5 Hit@5
 * @property reciprocalRank reciprocal rank(없으면 0)
 */
data class Phase2EvalRankResult(
    val rank: Int?,
    val hit1: Boolean,
    val hit3: Boolean,
    val hit5: Boolean,
    val reciprocalRank: Double,
)

/**
 * 리트리벌 결과와 기대값을 비교해 랭크 기반 점수를 계산한다.
 */
object Phase2EvalScorer {
    /**
     * @param item 평가 항목
     * @param candidates 리트리벌 후보(순위 순)
     * @return 랭크 기반 평가 결과
     */
    fun score(item: Phase2EvalItem, candidates: List<ChunkCandidate>): Phase2EvalRankResult {
        val expected = item.expected
        val matchedIndex = candidates.indexOfFirst { candidate ->
            if (expected == null) return@indexOfFirst false
            when {
                expected.chunkIds.isNotEmpty() -> candidate.id in expected.chunkIds
                expected.documentId != null -> candidate.documentId == expected.documentId
                !expected.documentTitleContains.isNullOrBlank() ->
                    candidate.documentTitle.contains(expected.documentTitleContains, ignoreCase = true)
                else -> false
            }
        }

        val rank = if (matchedIndex >= 0) matchedIndex + 1 else null
        return Phase2EvalRankResult(
            rank = rank,
            hit1 = rank == 1,
            hit3 = rank != null && rank <= 3,
            hit5 = rank != null && rank <= 5,
            reciprocalRank = rank?.let { 1.0 / it.toDouble() } ?: 0.0,
        )
    }
}
