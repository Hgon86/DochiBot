package com.dochibot.domain.enums

/**
 * 문서 처리 상태 enum.
 * PENDING: 처리 대기
 * PROCESSING: 처리 중
 * COMPLETED: 처리 완료
 * FAILED: 처리 실패
 */
enum class DocumentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
