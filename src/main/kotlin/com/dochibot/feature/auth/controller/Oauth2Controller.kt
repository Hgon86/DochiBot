package com.dochibot.feature.auth.controller

import com.dochibot.feature.auth.config.DochibotWebProperties
import com.dochibot.feature.auth.config.OAuth2Properties
import com.dochibot.feature.auth.service.AuthService
import com.dochibot.feature.auth.util.AuthCookies
import com.dochibot.feature.auth.util.JwtUtils
import com.dochibot.common.exception.CommonErrorCode
import com.dochibot.common.exception.DochiException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

/**
 * OAuth2 소셜 로그인 API 컨트롤러.
 *
 * Google OAuth2 인증 플로우를 처리한다.
 */
@RestController
@RequestMapping("/api/v1/auth/oauth2")
class Oauth2Controller(
    private val authService: AuthService,
    private val oauth2Properties: OAuth2Properties,
    private val jwtUtils: JwtUtils,
    private val dochibotWebProperties: DochibotWebProperties
) {
    private val log = KotlinLogging.logger {}

    /**
     * OAuth2 인증을 시작한다. Provider의 Authorization URL로 리다이렉트한다.
     *
     * @param provider OAuth2 제공자 ("google")
     * @param redirectUri (선택) 프론트엔드 리다이렉트 URI (state 파라미터로 전달 가능)
     * @return 302 Redirect to Provider Authorization URL
     */
    @GetMapping("/authorize/{provider}")
    suspend fun authorize(
        @PathVariable provider: String,
        @RequestParam(required = false) redirectUri: String?
    ): ResponseEntity<Unit> {
        log.info { "OAuth2 authorize requested: provider=$provider, hasRedirectUri=${!redirectUri.isNullOrBlank()}" }
        // 1. Provider 검증 (google만 허용)
        if (provider.lowercase() != "google") {
            throw DochiException(CommonErrorCode.BAD_REQUEST, "Unsupported provider: $provider")
        }

        // 2. Callback URI 구성
        val callbackUri = "${dochibotWebProperties.oauth2.backendBaseUrl}/api/v1/auth/oauth2/callback/$provider"

        // 3. Authorization URL 생성 (Google)
        // NOTE: redirectUri/state 처리는 추후 CSRF(state) 설계와 함께 정리한다.
        val authorizationUrl = UriComponentsBuilder
            .fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
            .queryParam("client_id", oauth2Properties.google.clientId)
            .queryParam("redirect_uri", callbackUri)
            .queryParam("response_type", "code")
            .queryParam("scope", "email profile")
            .build()
            .encode()
            .toUriString()

        // 4. 302 리다이렉트 응답
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(authorizationUrl))
            .build()
    }

    /**
     * OAuth2 콜백을 처리한다. Provider에서 전달받은 code로 JWT를 발급한다.
     *
     * @param provider OAuth2 제공자 ("google")
     * @param code OAuth2 Authorization Code
     * @param state (선택) 프론트엔드에서 전달한 state 파라미터 (CSRF 방지용)
     * @return 302 Redirect to frontend callback route
     */
    @GetMapping("/callback/{provider}")
    suspend fun callback(
        @PathVariable provider: String,
        @RequestParam code: String,
        @RequestParam(required = false) state: String?
    ): ResponseEntity<Unit> {
        log.info { "OAuth2 callback requested: provider=$provider, hasState=${!state.isNullOrBlank()}" }
        // 1. Provider 검증 (google만 허용)
        if (provider.lowercase() != "google") {
            throw DochiException(CommonErrorCode.BAD_REQUEST, "Unsupported provider: $provider")
        }

        // 2. AuthService를 통해 OAuth2 로그인 처리 (사용자 조회/생성 + JWT 발급)
        val issued = authService.loginOrRegisterOauth2(provider, code)

        // 브라우저 리다이렉트로 콜백이 호출되므로, 프론트 라우트로 다시 이동시켜
        // refresh_token 쿠키 기반으로 access token을 확보하도록 한다.
        val redirectTo = "${dochibotWebProperties.oauth2.frontendBaseUrl}/oauth/callback"

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(redirectTo))
            .header(
                AuthCookies.SET_COOKIE_HEADER,
                AuthCookies.refreshTokenCookie(
                    value = issued.refreshToken,
                    cookieSecure = dochibotWebProperties.cookie.secure,
                    maxAge = jwtUtils.refreshTokenTtl()
                ).toString()
            )
            .build()
    }
}
