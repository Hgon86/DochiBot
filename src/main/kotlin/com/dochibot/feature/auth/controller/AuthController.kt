package com.dochibot.feature.auth.controller

import com.dochibot.feature.auth.dto.LoginRequest
import com.dochibot.feature.auth.dto.LoginResponse
import com.dochibot.feature.auth.dto.MeResponse
import com.dochibot.feature.auth.dto.RefreshResponse
import com.dochibot.feature.auth.config.DochibotWebProperties
import com.dochibot.feature.auth.exception.AuthErrorCode
import com.dochibot.feature.auth.service.AuthService
import com.dochibot.feature.auth.util.AuthCookies
import com.dochibot.feature.auth.util.JwtUtils
import com.dochibot.common.exception.DochiException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * 인증 관련 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtUtils: JwtUtils,
    private val dochibotWebProperties: DochibotWebProperties
) {
    private val log = KotlinLogging.logger {}

    /**
     * 아이디/비밀번호로 로그인한다.
     *
     * @param request 로그인 요청
     * @return 로그인 결과(토큰/사용자 정보)
     */
    @PostMapping("/login")
    suspend fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        log.info { "Login requested: username=${request.username}" }
        val issued = authService.login(request.username, request.password)
        val body = LoginResponse(
            accessToken = issued.accessToken,
            expiresInSeconds = issued.expiresInSeconds,
            user = issued.user ?: throw IllegalStateException("User info is required")
        )
        return ResponseEntity.ok()
            .header(
                AuthCookies.SET_COOKIE_HEADER,
                AuthCookies.refreshTokenCookie(
                    value = issued.refreshToken,
                    cookieSecure = dochibotWebProperties.cookie.secure,
                    maxAge = jwtUtils.refreshTokenTtl()
                ).toString()
            )
            .body(body)
    }

    /**
     * Refresh Token으로 Access/Refresh 토큰을 재발급한다.
     *
     * @param refreshToken Refresh Token 쿠키
     * @return 재발급 결과(토큰)
     */
    @PostMapping("/refresh")
    suspend fun refresh(
        @CookieValue(name = AuthCookies.REFRESH_TOKEN_COOKIE_NAME, required = false) refreshToken: String?
    ): ResponseEntity<RefreshResponse> {
        log.info { "Refresh requested: hasRefreshToken=${!refreshToken.isNullOrBlank()}" }
        val token = refreshToken ?: throw DochiException(AuthErrorCode.REFRESH_TOKEN_INVALID)
        val issued = authService.refresh(token)
        val body = RefreshResponse(
            accessToken = issued.accessToken,
            expiresInSeconds = issued.expiresInSeconds
        )
        return ResponseEntity.ok()
            .header(
                AuthCookies.SET_COOKIE_HEADER,
                AuthCookies.refreshTokenCookie(
                    value = issued.refreshToken,
                    cookieSecure = dochibotWebProperties.cookie.secure,
                    maxAge = jwtUtils.refreshTokenTtl()
                ).toString()
            )
            .body(body)
    }

    /**
     * Refresh Token을 무효화하여 로그아웃 처리한다.
     *
     * @param refreshToken Refresh Token 쿠키
     * @return 성공 여부
     */
    @PostMapping("/logout")
    suspend fun logout(
        @CookieValue(name = AuthCookies.REFRESH_TOKEN_COOKIE_NAME, required = false) refreshToken: String?
    ): ResponseEntity<Map<String, Boolean>> {
        log.info { "Logout requested: hasRefreshToken=${!refreshToken.isNullOrBlank()}" }
        if (!refreshToken.isNullOrBlank()) {
            authService.logout(refreshToken)
        }
        return ResponseEntity.ok()
            .header(
                AuthCookies.SET_COOKIE_HEADER,
                AuthCookies.deleteRefreshTokenCookie(cookieSecure = dochibotWebProperties.cookie.secure).toString()
            )
            .body(mapOf("success" to true))
    }

    /**
     * 현재 인증된 사용자 정보를 조회한다.
     *
     * @param jwt 인증 주체(JWT)
     * @return 사용자 정보
     */
    @GetMapping("/me")
    suspend fun me(@AuthenticationPrincipal jwt: Jwt): MeResponse {
        val userId = UUID.fromString(jwt.subject)
        log.info { "Me requested: userId=$userId" }
        return authService.me(userId)
    }
}
