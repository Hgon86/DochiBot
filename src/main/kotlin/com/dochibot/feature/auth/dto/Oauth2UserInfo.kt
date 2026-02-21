package com.dochibot.feature.auth.dto

/**
 * OAuth2 Provider에서 조회한 사용자 정보 DTO.
 *
 * @property providerId OAuth2 Provider의 사용자 고유 ID (Google: sub, GitHub: id)
 * @property email 사용자 이메일 주소
 * @property name 사용자 이름 (nullable)
 */
data class Oauth2UserInfo(
    val providerId: String,
    val email: String,
    val name: String?
)

