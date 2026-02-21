package com.dochibot.feature.retrieval.application.rerank

/**
 * 리랭커 구현 타입.
 */
enum class RerankModel {
    HEURISTIC,
    LLM_JUDGE,
    CROSS_ENCODER,
}
