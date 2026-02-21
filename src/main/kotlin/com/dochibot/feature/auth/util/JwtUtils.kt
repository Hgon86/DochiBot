package com.dochibot.feature.auth.util

import com.dochibot.common.util.id.Uuid7Generator
import com.dochibot.domain.entity.User
import com.dochibot.domain.enums.AuthProvider
import com.dochibot.domain.enums.UserRole
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * JWT 생성/디코딩 유틸리티.
 */
@Component
class JwtUtils(
    private val jwtEncoder: JwtEncoder,
    private val jwtDecoder: ReactiveJwtDecoder,
    @Value("\${dochibot.jwt.access-token-ttl-seconds:43200}")
    private val accessTtlSeconds: Long,
    @Value("\${dochibot.jwt.refresh-token-ttl-seconds:604800}")
    private val refreshTtlSeconds: Long
) {
    /**
     * Access Token TTL.
     *
     * @return TTL
     */
    fun accessTokenTtl(): Duration = Duration.ofSeconds(accessTtlSeconds)

    /**
     * Refresh Token TTL.
     *
     * @return TTL
     */
    fun refreshTokenTtl(): Duration = Duration.ofSeconds(refreshTtlSeconds)

    /**
     * Access Token을 생성한다.
     *
     * @param user 토큰 주체(사용자)
     * @param now 발급 시각
     * @return 생성된 토큰
     */
    fun generateAccessToken(user: User, now: Instant = Instant.now()): GeneratedToken {
        return generateToken(
            user = user,
            tokenType = "access",
            ttl = accessTokenTtl(),
            now = now
        )
    }

    /**
     * Refresh Token을 생성한다.
     *
     * @param user 토큰 주체(사용자)
     * @param now 발급 시각
     * @return 생성된 토큰
     */
    fun generateRefreshToken(user: User, now: Instant = Instant.now()): GeneratedToken {
        return generateToken(
            user = user,
            tokenType = "refresh",
            ttl = refreshTokenTtl(),
            now = now
        )
    }

    /**
     * JWT 문자열을 디코딩한다.
     *
     * @param token JWT 문자열
     * @return 디코딩된 JWT
     */
    suspend fun decode(token: String): Jwt {
        return jwtDecoder.decode(token).awaitSingle()
    }

    /**
     * tokenType/TTL 등을 포함한 토큰을 생성한다.
     *
     * @param user 토큰 주체(사용자)
     * @param tokenType 토큰 타입(access/refresh)
     * @param ttl TTL
     * @param now 발급 시각
     * @return 생성된 토큰
     */
    private fun generateToken(
        user: User,
        tokenType: String,
        ttl: Duration,
        now: Instant
    ): GeneratedToken {
        val issuedAt = now
        val expiresAt = now.plus(ttl)
        val jti = Uuid7Generator.create().toString()

        val headers = JwsHeader.with(MacAlgorithm.HS256).build()

        val claims = JwtClaimsSet.builder()
            .id(jti)
            .subject(user.id.toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("tokenType", tokenType)
            .claim("username", user.username)
            .claim("role", user.role.name)
            .claim("provider", user.provider.name)
            .build()

        val tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).tokenValue
        return GeneratedToken(
            token = tokenValue,
            tokenId = jti,
            expiresAt = expiresAt
        )
    }

    /**
     * 생성된 JWT 결과.
     */
    data class GeneratedToken(
        val token: String,
        val tokenId: String,
        val expiresAt: Instant
    )

    companion object {
        /**
         * role 클레임을 [UserRole]로 변환한다.
         *
         * @param value role 클레임 값
         * @return 변환 결과(없거나 파싱 실패 시 null)
         */
        fun roleFromClaim(value: String?): UserRole? = value?.let { UserRole.valueOf(it) }

        /**
         * provider 클레임을 [AuthProvider]로 변환한다.
         *
         * @param value provider 클레임 값
         * @return 변환 결과(없거나 파싱 실패 시 null)
         */
        fun providerFromClaim(value: String?): AuthProvider? = value?.let { AuthProvider.valueOf(it) }
    }
}
