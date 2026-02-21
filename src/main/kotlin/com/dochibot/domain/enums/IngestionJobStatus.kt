package com.dochibot.domain.enums

/**
 * 인덱싱 작업 상태 enum.
 * QUEUED: 작업 대기
 * RUNNING: 실행 중
 * SUCCEEDED: 성공
 * FAILED: 실패
 */
enum class IngestionJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED
}
