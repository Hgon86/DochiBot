package com.dochibot.feature.retrieval.application.rerank

import com.dochibot.feature.retrieval.dto.ChunkCandidate

/**
 * 리랭커 입력 모델.
 *
 * @property query 사용자 질의
 * @property candidates 리랭크 대상 후보
 */
data class RerankInput(
    val query: String,
    val candidates: List<ChunkCandidate>,
)
