package com.dochibot.feature.retrieval.application.verify

import com.dochibot.feature.retrieval.dto.ChunkCandidate

/**
 * 리트리벌 결과가 질문에 대한 "근거"로 충분한지 검증한다.
 */
interface EvidenceVerifier {
    /**
     * @param queryText 사용자 질의 원문
     * @param chunks 최종 컨텍스트 후보(topN)
     * @return 검증 결과
     */
    fun verify(queryText: String, chunks: List<ChunkCandidate>): EvidenceVerification
}
