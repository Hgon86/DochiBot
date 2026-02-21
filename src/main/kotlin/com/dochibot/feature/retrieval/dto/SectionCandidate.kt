package com.dochibot.feature.retrieval.dto

import java.util.UUID

/**
 * 섹션 검색 후보.
 *
 * @property id 섹션 ID
 * @property documentId 문서 ID
 * @property documentTitle 문서 제목
 * @property heading 섹션 제목
 * @property sectionPath 섹션 경로(트리/조항 경로)
 * @property distance (옵션) dense 검색 cosine distance (작을수록 유사)
 * @property sparseScore (옵션) FTS(ts_rank_cd) 점수 (클수록 유사)
 * @property denseRank (옵션) dense 결과 내 순위(1부터)
 * @property sparseRank (옵션) sparse 결과 내 순위(1부터)
 * @property rrfScore RRF 결합 점수(클수록 우선)
 */
data class SectionCandidate(
    val id: UUID,
    val documentId: UUID,
    val documentTitle: String,
    val heading: String,
    val sectionPath: String,
    val distance: Double? = null,
    val sparseScore: Double? = null,
    val denseRank: Int? = null,
    val sparseRank: Int? = null,
    val rrfScore: Double = 0.0,
)
