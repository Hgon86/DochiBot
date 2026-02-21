package com.dochibot.feature.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * OAuth2 클라이언트 설정 Properties.
 *
 * application.yml의 spring.security.oauth2.client.registration 설정을 바인딩한다.
 *
 * @property google Google OAuth2 클라이언트 설정
 */
@ConfigurationProperties(prefix = "spring.security.oauth2.client.registration")
data class OAuth2Properties(
    val google: ClientRegistration
) {
    /**
     * OAuth2 클라이언트 등록 정보.
     *
     * @property clientId OAuth2 클라이언트 ID
     * @property clientSecret OAuth2 클라이언트 Secret
     */
    data class ClientRegistration(
        val clientId: String,
        val clientSecret: String
    )
}
