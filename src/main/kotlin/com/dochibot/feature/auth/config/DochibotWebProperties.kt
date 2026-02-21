package com.dochibot.feature.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

/**
 * 인증/쿠키/OAuth2 관련 런타임 설정.
 *
 * @property cookie 쿠키 설정
 * @property oauth2 OAuth2 플로우 관련 URL 설정
 */
@ConfigurationProperties(prefix = "dochibot")
data class DochibotWebProperties(
    @NestedConfigurationProperty
    val cookie: Cookie = Cookie(),
    @NestedConfigurationProperty
    val oauth2: OAuth2 = OAuth2(),
) {
    /**
     * 쿠키 설정.
     *
     * @property secure HTTPS 환경에서만 쿠키를 전송한다.
     */
    data class Cookie(
        val secure: Boolean = false,
    )

    /**
     * OAuth2 플로우 관련 URL 설정.
     *
     * @property backendBaseUrl Google redirect_uri로 사용되는 백엔드 base URL
     * @property frontendBaseUrl OAuth2 완료 후 리다이렉트할 프론트 base URL
     */
    data class OAuth2(
        val backendBaseUrl: String = "http://localhost:8080",
        val frontendBaseUrl: String = "http://localhost:5173",
    )
}
