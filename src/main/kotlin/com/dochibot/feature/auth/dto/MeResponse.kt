package com.dochibot.feature.auth.dto

import com.dochibot.domain.enums.AuthProvider
import com.dochibot.domain.enums.UserRole
import java.time.Instant
import java.util.UUID

/**
 * 내 정보 조회 응답 DTO.
 *
 * @property id 사용자 ID
 * @property username 로그인 아이디
 * @property role 사용자 역할
 * @property provider 인증 제공자
 * @property createdAt 생성 시각(없으면 null)
 */
data class MeResponse(
    val id: UUID,
    val username: String,
    val role: UserRole,
    val provider: AuthProvider,
    val createdAt: Instant?
)
