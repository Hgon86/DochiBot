package com.dochibot.feature.auth.util

import org.springframework.http.ResponseCookie
import java.time.Duration

/**
 * 인증 쿠키 유틸.
 */
object AuthCookies {
    const val REFRESH_TOKEN_COOKIE_NAME: String = "refresh_token"
    const val SET_COOKIE_HEADER: String = "Set-Cookie"
    private const val DEFAULT_SAME_SITE: String = "Lax"

    /**
     * Refresh Token을 HttpOnly 쿠키로 생성한다.
     */
    fun refreshTokenCookie(
        value: String,
        cookieSecure: Boolean,
        maxAge: Duration,
        sameSite: String = DEFAULT_SAME_SITE
    ): ResponseCookie {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, value)
            .httpOnly(true)
            .path("/")
            .sameSite(sameSite)
            .secure(cookieSecure)
            .maxAge(maxAge)
            .build()
    }

    /**
     * Refresh Token 쿠키를 삭제한다.
     */
    fun deleteRefreshTokenCookie(
        cookieSecure: Boolean,
        sameSite: String = DEFAULT_SAME_SITE
    ): ResponseCookie {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
            .httpOnly(true)
            .path("/")
            .sameSite(sameSite)
            .secure(cookieSecure)
            .maxAge(Duration.ZERO)
            .build()
    }
}
