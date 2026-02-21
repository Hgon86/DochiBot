package com.dochibot.common.util.log

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest

/**
 * 구조화 로그 출력에 필요한 공통 유틸리티.
 */
object StructuredLogSupport {
    /**
     * 질의 텍스트를 고정 길이 해시로 변환한다.
     *
     * @param queryText 원본 질의 텍스트
     * @return SHA-256 앞 16자 해시 문자열
     */
    fun hashQuery(queryText: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(queryText.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }.take(16)
    }

    /**
     * 구조화 로그 payload를 JSON 문자열로 변환한다.
     *
     * @param objectMapper JSON 직렬화기
     * @param payload 로그 payload
     * @return JSON 문자열(실패 시 fallback 문자열)
     */
    fun toJsonLog(objectMapper: ObjectMapper, payload: Map<String, Any?>): String {
        return runCatching { objectMapper.writeValueAsString(payload) }
            .getOrElse { "{\"event\":\"structured_log_error\",\"message\":\"${it.message}\"}" }
    }
}
