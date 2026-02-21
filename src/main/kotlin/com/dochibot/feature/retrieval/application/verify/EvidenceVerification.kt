package com.dochibot.feature.retrieval.application.verify

/**
 * 근거 검증 결과.
 *
 * @property isSufficient 근거가 충분하면 true
 * @property reason 부족 사유(로그/디버깅 용)
 * @property tokenCoverage query 토큰 커버리지(0~1)
 * @property top1Score top1 finalScore
 * @property top1Top2Gap top1-finalScore - top2-finalScore (top2가 없으면 null)
 * @property sameDocSupportCount 상위 후보 중 top1과 같은 documentId 개수
 * @property distinctDocCount 상위 후보의 서로 다른 documentId 개수
 * @property queryType 질의 유형
 * @property appliedMinTokenCoverage 질의 유형 보정을 반영한 coverage 임계치
 */
data class EvidenceVerification(
    val isSufficient: Boolean,
    val reason: String,
    val tokenCoverage: Double,
    val top1Score: Double? = null,
    val top1Top2Gap: Double? = null,
    val sameDocSupportCount: Int? = null,
    val distinctDocCount: Int? = null,
    val queryType: QueryType = QueryType.OTHER,
    val appliedMinTokenCoverage: Double? = null,
)
