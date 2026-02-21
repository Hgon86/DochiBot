package com.dochibot.feature.retrieval.application.verify

/**
 * 근거가 부족할 때의 정책 응답 전략.
 */
enum class VerifyPolicy {
    /**
     * "문서에서 찾을 수 없습니다."로 즉시 응답한다.
     */
    NO_EVIDENCE,

    /**
     * 답변 대신 추가 정보를 요청하여 질문을 좁힌다.
     */
    ASK_FOLLOWUP,
}
