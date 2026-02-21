package com.dochibot.domain.enums

/**
 * 인증 제공자 enum.
 *
 * CREDENTIALS: username/password 직접 인증
 * GOOGLE: Google OAuth2
 * GITHUB: GitHub OAuth2
 */
enum class AuthProvider {
    CREDENTIALS,
    GOOGLE,
    GITHUB
}
