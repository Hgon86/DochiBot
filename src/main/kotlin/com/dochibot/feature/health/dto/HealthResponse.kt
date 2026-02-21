package com.dochibot.feature.health.dto

/**
 * 헬스 체크 응답 DTO.
 *
 * @property status 서비스 상태("UP")
 */
data class HealthResponse(
    val status: String = "UP",
)
