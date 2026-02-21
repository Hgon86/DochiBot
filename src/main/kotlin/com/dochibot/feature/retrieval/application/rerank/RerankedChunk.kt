package com.dochibot.feature.retrieval.application.rerank

import com.dochibot.feature.retrieval.dto.ChunkCandidate

/**
 * 리랭크 결과 항목.
 *
 * @property candidate 원본 후보
 * @property score 리랭크 점수(클수록 우선)
 * @property rank 리랭크 순위(1부터)
 */
data class RerankedChunk(
    val candidate: ChunkCandidate,
    val score: Double,
    val rank: Int,
)
