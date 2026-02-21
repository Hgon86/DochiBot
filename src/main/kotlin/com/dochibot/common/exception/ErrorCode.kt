package com.dochibot.common.exception

import org.springframework.http.HttpStatus

/**
 * API 에러의 표준 메타데이터.
 *
 * - code: 클라이언트 분기용 안정적인 문자열 코드
 * - status: HTTP 상태 코드
 */
interface ErrorCode {
    val code: String
    val status: HttpStatus
}
