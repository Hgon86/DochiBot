package com.dochibot.feature.retrieval.application.rerank

/**
 * 리랭커 계약.
 */
fun interface Reranker {
    /**
     * @param input 질의/후보 입력
     * @return 점수 내림차순으로 정렬된 결과
     */
    suspend fun rerank(input: RerankInput): List<RerankedChunk>
}
