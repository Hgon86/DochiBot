package com.dochibot.feature.retrieval.application.verify

/**
 * 질의 유형.
 */
enum class QueryType {
    /**
     * 사실/정의/개체 식별 중심 질의.
     */
    WHAT_WHO,

    /**
     * 절차/원인/설명 중심 질의.
     */
    HOW_WHY,

    /**
     * 위 유형으로 분류되지 않는 일반 질의.
     */
    OTHER,
}
