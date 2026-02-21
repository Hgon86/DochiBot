package com.dochibot.feature.auth.service

import com.dochibot.feature.auth.config.OAuth2ClientConfig
import com.dochibot.feature.auth.config.OAuth2Properties
import com.dochibot.feature.auth.config.DochibotWebProperties
import com.dochibot.feature.auth.dto.Oauth2UserInfo
import com.dochibot.common.exception.CommonErrorCode
import com.dochibot.common.exception.DochiException
import com.dochibot.domain.entity.User
import com.dochibot.domain.enums.AuthProvider
import com.dochibot.domain.enums.UserRole
import com.dochibot.domain.repository.UserRepository
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.awaitBody

/**
 * OAuth2 소셜 로그인 사용자 관리 서비스.
 *
 * OAuth2 Provider(Google)에서 사용자 정보를 조회하고,
 * DB에 존재하지 않으면 자동으로 회원가입 처리한다.
 */
@Service
class OAuth2UserService(
    private val userRepository: UserRepository,
    private val oAuth2ClientConfig: OAuth2ClientConfig,
    private val oauth2Properties: OAuth2Properties,
    private val dochibotWebProperties: DochibotWebProperties
) {

    /**
     * OAuth2 Provider에서 사용자 정보를 조회하고, DB에 없으면 자동 회원가입한다.
     *
     * @param provider OAuth2 제공자 ("google")
     * @param code OAuth2 Authorization Code
     * @return 조회 또는 생성된 사용자 Entity
     */
    suspend fun getOrCreateUser(
        provider: String,
        code: String
    ): User {
        // 0. Provider 검증 (google만 허용)
        if (provider.lowercase() != "google") {
            throw DochiException(CommonErrorCode.BAD_REQUEST, "Unsupported provider: $provider")
        }

        // 1. Authorization Code를 Access Token으로 교환
        val accessToken = exchangeCodeForToken(provider, code)

        // 2. Access Token으로 사용자 정보 조회
        val userInfo = fetchUserInfo(provider, accessToken)

        // 3. DB에서 (provider, providerId) 기준으로 사용자 조회
        val existing = userRepository.findByProviderAndProviderId(
            provider = AuthProvider.valueOf(provider.uppercase()),
            providerId = userInfo.providerId
        )

        // 4. 이미 존재하면 기존 사용자 반환
        if (existing != null) {
            return existing
        }

        // 5. 없으면 새 사용자 생성 (자동 회원가입)
        val newUser = User.new(
            username = userInfo.email,
            passwordHash = null,  // OAuth 사용자는 비밀번호 없음
            role = UserRole.USER,
            provider = AuthProvider.valueOf(provider.uppercase()),
            providerId = userInfo.providerId,
            isActive = true
        )

        // 6. DB에 저장 후 반환
        return userRepository.save(newUser)
    }

    /**
     * OAuth2 Authorization Code를 Access Token으로 교환한다.
     *
     * @param provider OAuth2 제공자 ("google")
     * @param code OAuth2 Authorization Code
     * @return Access Token 문자열
     */
    private suspend fun exchangeCodeForToken(
        provider: String,
        code: String,
    ): String {
        // 1. Token Endpoint URL
        val tokenEndpoint = "https://oauth2.googleapis.com/token"

        // 2. client_id, client_secret
        val clientId = oauth2Properties.google.clientId
        val clientSecret = oauth2Properties.google.clientSecret

        // 3. Redirect URI 구성 (callback URL)
        val redirectUri = "${dochibotWebProperties.oauth2.backendBaseUrl}/api/v1/auth/oauth2/callback/$provider"

        // 4. WebClient로 POST 요청
        val webClient = oAuth2ClientConfig.oauth2WebClient()
        val response = webClient.post()
            .uri(tokenEndpoint)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("grant_type", "authorization_code")
                    .with("code", code)
                    .with("client_id", clientId)
                    .with("client_secret", clientSecret)
                    .with("redirect_uri", redirectUri)
            )
            .retrieve()
            .awaitBody<Map<String, Any>>()

        // 5. Response에서 access_token 추출
        return response["access_token"] as? String
            ?: throw DochiException(CommonErrorCode.INTERNAL_ERROR, "Access token not found in response")
    }

    /**
     * Access Token으로 OAuth2 Provider에서 사용자 정보를 조회한다.
     *
     * @param provider OAuth2 제공자 ("google")
     * @param accessToken Access Token 문자열
     * @return 사용자 정보 DTO
     */
    private suspend fun fetchUserInfo(
        provider: String,
        accessToken: String
    ): Oauth2UserInfo {
        // 1. UserInfo Endpoint URL
        val userInfoEndpoint = "https://www.googleapis.com/oauth2/v3/userinfo"

        // 2. WebClient로 GET 요청
        val webClient = oAuth2ClientConfig.oauth2WebClient()
        val response = webClient.get()
            .uri(userInfoEndpoint)
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .awaitBody<Map<String, Any>>()

        // 3. 응답 파싱 (Google)
        // Google: { "sub": "xxx", "email": "user@gmail.com", "name": "User" }
        return Oauth2UserInfo(
            providerId = response["sub"] as? String
                ?: throw DochiException(CommonErrorCode.INTERNAL_ERROR, "Google sub not found"),
            email = response["email"] as? String
                ?: throw DochiException(CommonErrorCode.INTERNAL_ERROR, "Google email not found"),
            name = response["name"] as? String
        )
    }
}
