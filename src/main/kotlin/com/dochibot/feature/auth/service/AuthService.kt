package com.dochibot.feature.auth.service

import com.dochibot.feature.auth.dto.LoginResponse
import com.dochibot.feature.auth.dto.MeResponse
import com.dochibot.feature.auth.exception.AuthErrorCode
import com.dochibot.feature.auth.util.JwtUtils
import com.dochibot.common.exception.CommonErrorCode
import com.dochibot.common.exception.DochiException
import com.dochibot.domain.enums.AuthProvider
import com.dochibot.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

/**
 * 로그인/토큰 재발급/로그아웃/내 정보 조회를 제공하는 인증 서비스.
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtils: JwtUtils,
    private val refreshTokenService: RefreshTokenService,
    private val oauth2UserService: OAuth2UserService
) {

    /**
     * Access/Refresh 토큰 발급 결과.
     *
     * Refresh Token은 컨트롤러에서 HttpOnly 쿠키로 내려준다.
     */
    data class IssuedTokens(
        val accessToken: String,
        val refreshToken: String,
        val expiresInSeconds: Long,
        val user: LoginResponse.UserInfo? = null
    )
    /**
     * 사용자명/비밀번호 기반 로그인.
     *
     * @param username 로그인 아이디
     * @param password 원문 비밀번호
     * @return 로그인 결과(토큰/사용자 정보)
     */
    @Transactional
    suspend fun login(username: String, password: String): IssuedTokens {
        val user = userRepository.findByUsername(username)
            ?: throw DochiException(AuthErrorCode.INVALID_CREDENTIALS)

        if (!user.isActive) {
            throw DochiException(AuthErrorCode.USER_INACTIVE)
        }

        if (user.provider != AuthProvider.CREDENTIALS) {
            throw DochiException(AuthErrorCode.INVALID_CREDENTIALS)
        }

        val passwordHash = user.passwordHash
            ?: throw DochiException(AuthErrorCode.INVALID_CREDENTIALS)

        if (!passwordEncoder.matches(password, passwordHash)) {
            throw DochiException(AuthErrorCode.INVALID_CREDENTIALS)
        }

        val now = Instant.now()
        val access = jwtUtils.generateAccessToken(user, now)
        val refresh = jwtUtils.generateRefreshToken(user, now)

        refreshTokenService.save(
            tokenId = refresh.tokenId,
            userId = user.id,
            provider = user.provider,
            expiresAt = refresh.expiresAt,
            ttl = jwtUtils.refreshTokenTtl()
        )

        return IssuedTokens(
            accessToken = access.token,
            refreshToken = refresh.token,
            expiresInSeconds = jwtUtils.accessTokenTtl().seconds,
            user = LoginResponse.UserInfo(
                id = user.id,
                username = user.username,
                role = user.role,
                provider = user.provider
            )
        )
    }

    /**
     * Refresh Token 기반으로 토큰을 재발급한다.
     *
     * @param refreshToken Refresh Token
     * @return 재발급 결과(토큰)
     */
    @Transactional
    suspend fun refresh(refreshToken: String): IssuedTokens {
        val jwt = decodeRefreshToken(refreshToken)

        val tokenId = jwt.id
            ?: throw DochiException(AuthErrorCode.REFRESH_TOKEN_INVALID)

        val stored = refreshTokenService.find(tokenId)
            ?: throw DochiException(AuthErrorCode.REFRESH_TOKEN_INVALID)

        if (stored.expiresAt.isBefore(Instant.now())) {
            refreshTokenService.delete(tokenId)
            throw DochiException(AuthErrorCode.REFRESH_TOKEN_EXPIRED)
        }

        val userId = UUID.fromString(jwt.subject)
        val user = userRepository.findById(userId)
            ?: throw DochiException(CommonErrorCode.NOT_FOUND)

        if (!user.isActive) {
            refreshTokenService.delete(tokenId)
            throw DochiException(AuthErrorCode.USER_INACTIVE)
        }

        // 로테이션: 이전 토큰 무효화
        refreshTokenService.delete(tokenId)

        val now = Instant.now()
        val access = jwtUtils.generateAccessToken(user, now)
        val newRefresh = jwtUtils.generateRefreshToken(user, now)
        refreshTokenService.save(
            tokenId = newRefresh.tokenId,
            userId = user.id,
            provider = user.provider,
            expiresAt = newRefresh.expiresAt,
            ttl = jwtUtils.refreshTokenTtl()
        )

        return IssuedTokens(
            accessToken = access.token,
            refreshToken = newRefresh.token,
            expiresInSeconds = jwtUtils.accessTokenTtl().seconds
        )
    }

    /**
     * Refresh Token을 무효화한다. (멱등)
     *
     * @param refreshToken Refresh Token
     */
    suspend fun logout(refreshToken: String) {
        val jwt = try {
            decodeRefreshToken(refreshToken)
        } catch (e: Exception) {
            if (e is CancellationException) {
                throw e
            }
            // 로그아웃은 멱등 처리: 토큰이 깨졌어도 성공처럼 처리
            return
        }

        val tokenId = jwt.id ?: return
        refreshTokenService.delete(tokenId)
    }

    /**
     * 사용자 ID로 내 정보를 조회한다.
     *
     * @param userId 사용자 ID
     * @return 사용자 정보
     */
    suspend fun me(userId: UUID): MeResponse {
        val user = userRepository.findById(userId)
            ?: throw DochiException(CommonErrorCode.NOT_FOUND)
        return MeResponse(
            id = user.id,
            username = user.username,
            role = user.role,
            provider = user.provider,
            createdAt = user.createdAt
        )
    }

    /**
     * Refresh Token을 디코딩하고 tokenType 유효성을 검증한다.
     *
     * @param refreshToken Refresh Token
     * @return 디코딩된 JWT
     */
    private suspend fun decodeRefreshToken(refreshToken: String): Jwt {
        val jwt = try {
            jwtUtils.decode(refreshToken)
        } catch (e: Exception) {
            if (e is CancellationException) {
                throw e
            }
            throw DochiException(errorCode = AuthErrorCode.REFRESH_TOKEN_INVALID, cause = e)
        }
        val tokenType = jwt.getClaimAsString("tokenType")
        if (tokenType != "refresh") {
            throw DochiException(AuthErrorCode.REFRESH_TOKEN_INVALID)
        }
        return jwt
    }

    /**
     * OAuth2 로그인 또는 자동 회원가입 후 JWT를 발급한다.
     *
     * @param provider OAuth2 제공자 ("google")
     * @param code OAuth2 Authorization Code
     * @return 로그인 결과(토큰/사용자 정보)
     */
    @Transactional
    suspend fun loginOrRegisterOauth2(
        provider: String,
        code: String,
    ): IssuedTokens {
        // 1. OAuth2UserService를 통해 사용자 조회 또는 자동 회원가입
        val user = oauth2UserService.getOrCreateUser(provider, code)

        // 2. 비활성 사용자 체크
        if (!user.isActive) {
            throw DochiException(AuthErrorCode.USER_INACTIVE)
        }

        // 3. 현재 시각 기준으로 JWT 생성
        val now = Instant.now()
        val access = jwtUtils.generateAccessToken(user, now)
        val refresh = jwtUtils.generateRefreshToken(user, now)

        // 4. Refresh Token을 Redis에 저장
        refreshTokenService.save(
            tokenId = refresh.tokenId,
            userId = user.id,
            provider = user.provider,
            expiresAt = refresh.expiresAt,
            ttl = jwtUtils.refreshTokenTtl()
        )

        // 5. 토큰 발급 결과 반환
        return IssuedTokens(
            accessToken = access.token,
            refreshToken = refresh.token,
            expiresInSeconds = jwtUtils.accessTokenTtl().seconds,
            user = LoginResponse.UserInfo(
                id = user.id,
                username = user.username,
                role = user.role,
                provider = user.provider
            )
        )
    }
}
