package com.dochibot.common.exception

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

/**
 * 공통 에러 응답 포맷.
 * docs/phase1-api.md 의 0.3 규격을 따른다.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class ApiErrorResponse(
    val timestamp: Instant = Instant.now(),
    val status: Int,
    val code: String,
    val message: String,
    val i18nKey: String? = null,
    val i18nArgs: List<String> = emptyList(),
    val detail: String? = null,
    val errors: List<FieldError> = emptyList(),
    val traceId: String? = null
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class FieldError(
    val field: String,
    val value: String? = null,
    val reason: String
)
