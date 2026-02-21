package com.dochibot.feature.retrieval.application

/**
 * RRF(Reciprocal Rank Fusion) 결합 점수 계산기.
 */
object RrfFusion {
    /**
     * @param rank 1부터 시작하는 순위
     * @param k RRF 상수(기본 60)
     * @return RRF 점수
     */
    fun score(rank: Int, k: Int = 60): Double {
        require(rank >= 1) { "rank must be >= 1" }
        return 1.0 / (k + rank)
    }
}
