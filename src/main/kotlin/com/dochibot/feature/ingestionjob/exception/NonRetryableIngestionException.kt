package com.dochibot.feature.ingestionjob.exception

/**
 * 재시도해도 성공할 가능성이 낮은 인제션 오류.
 * 예: 설정 불일치, 지원하지 않는 포맷, 파일 크기 제한 초과 등
 */
class NonRetryableIngestionException(message: String) : RuntimeException(message)
