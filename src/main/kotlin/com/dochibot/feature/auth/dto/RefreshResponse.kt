package com.dochibot.feature.auth.dto

/**
 * 토큰 재발급 응답 DTO.
 *
 * @property accessToken Access Token
 * @property tokenType 토큰 타입(Bearer)
 * @property expiresInSeconds 만료까지 남은 시간(초)
 */
data class RefreshResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long
)
