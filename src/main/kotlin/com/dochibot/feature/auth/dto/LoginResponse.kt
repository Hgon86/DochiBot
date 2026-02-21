package com.dochibot.feature.auth.dto

import com.dochibot.domain.enums.AuthProvider
import com.dochibot.domain.enums.UserRole
import java.util.UUID

/**
 * 로그인 성공 응답 DTO.
 *
 * @property accessToken Access Token
 * @property tokenType 토큰 타입(Bearer)
 * @property expiresInSeconds 만료까지 남은 시간(초)
 * @property user 사용자 요약 정보
 */
data class LoginResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long,
    val user: UserInfo
) {
    /**
     * 토큰과 함께 반환하는 사용자 요약 정보.
     *
     * @property id 사용자 ID
     * @property username 로그인 아이디
     * @property role 사용자 역할
     * @property provider 인증 제공자
     */
    data class UserInfo(
        val id: UUID,
        val username: String,
        val role: UserRole,
        val provider: AuthProvider
    )
}
