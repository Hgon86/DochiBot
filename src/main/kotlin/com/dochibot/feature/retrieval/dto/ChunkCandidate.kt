package com.dochibot.feature.retrieval.dto

import java.util.UUID

/**
 * 청크 검색 후보.
 *
 * 점수 필드 의미:
 * - [rrfScore]: RRF(Rank Reciprocal Fusion) 융합 점수. dense/sparse 순위 결합 결과
 * - [rerankScore]: 리랭커가 재계산한 점수. 리랭크 미수행 시 null
 * - [finalScore]: 최종 순위 결정에 사용되는 점수.
 *   - 리랭크 미사용: rrfScore와 동일
 *   - 리랭크 사용: rerankScore와 동일
 *
 * @property id 청크 ID
 * @property documentId 문서 ID
 * @property documentTitle 문서 제목
 * @property sectionId 섹션 ID
 * @property sectionPath 섹션 경로
 * @property text 청크 본문
 * @property page (옵션) PDF 페이지
 * @property distance (옵션) dense 검색 cosine distance (작을수록 유사)
 * @property sparseScore (옵션) FTS(ts_rank_cd) 점수 (클수록 유사)
 * @property denseRank (옵션) dense 결과 내 순위(1부터)
 * @property sparseRank (옵션) sparse 결과 내 순위(1부터)
 * @property rrfScore RRF 융합 점수 (dense/sparse 결합)
 * @property rerankScore 리랭커 점수 (미수행 시 null)
 * @property finalScore 최종 순위 점수 (리랭크 여부에 따라 rrfScore 또는 rerankScore)
 */
data class ChunkCandidate(
    val id: UUID,
    val documentId: UUID,
    val documentTitle: String,
    val sectionId: UUID,
    val sectionPath: String? = null,
    val text: String,
    val page: Int? = null,
    val distance: Double? = null,
    val sparseScore: Double? = null,
    val denseRank: Int? = null,
    val sparseRank: Int? = null,
    val rrfScore: Double = 0.0,
    val rerankScore: Double? = null,
    val finalScore: Double = 0.0,
) {
    /**
     * 리랭크 점수를 적용하여 새 인스턴스를 생성한다.
     *
     * @param score 리랭커가 계산한 점수
     * @return rerankScore와 finalScore가 설정된 새 ChunkCandidate
     */
    fun withRerankScore(score: Double): ChunkCandidate =
        copy(rerankScore = score, finalScore = score)
}
