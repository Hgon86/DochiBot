package com.dochibot.feature.auth.service

import com.dochibot.domain.enums.AuthProvider
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Refresh Token 상태를 Redis에 저장/조회/삭제한다.
 */
@Service
class RefreshTokenService(
    private val redis: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    /**
     * Refresh Token 메타데이터를 저장한다.
     *
     * @param tokenId 토큰 ID(JTI)
     * @param userId 사용자 ID
     * @param provider 인증 제공자
     * @param expiresAt 만료 시각
     * @param ttl Redis TTL
     */
    suspend fun save(
        tokenId: String,
        userId: UUID,
        provider: AuthProvider,
        expiresAt: Instant,
        ttl: Duration
    ) {
        val data = RefreshTokenData(
            tokenId = tokenId,
            userId = userId,
            provider = provider,
            createdAt = Instant.now(),
            expiresAt = expiresAt
        )
        val value = objectMapper.writeValueAsString(data)
        redis.opsForValue().set(key(tokenId), value, ttl).awaitSingle()
    }

    /**
     * tokenId로 Refresh Token 메타데이터를 조회한다.
     *
     * @param tokenId 토큰 ID(JTI)
     * @return 저장된 데이터(없으면 null)
     */
    suspend fun find(tokenId: String): RefreshTokenData? {
        val json = redis.opsForValue().get(key(tokenId)).awaitSingleOrNull() ?: return null
        return objectMapper.readValue(json, RefreshTokenData::class.java)
    }

    /**
     * tokenId에 해당하는 Refresh Token을 삭제한다.
     *
     * @param tokenId 토큰 ID(JTI)
     * @return 삭제 성공 여부
     */
    suspend fun delete(tokenId: String): Boolean {
        return redis.delete(key(tokenId)).awaitSingle() > 0
    }

    /**
     * Redis 저장 키를 생성한다.
     *
     * @param tokenId 토큰 ID(JTI)
     * @return Redis 키
     */
    private fun key(tokenId: String): String = "refresh_token:$tokenId"

    /**
     * Redis에 저장되는 Refresh Token 데이터.
     */
    data class RefreshTokenData(
        val tokenId: String,
        val userId: UUID,
        val provider: AuthProvider,
        val createdAt: Instant,
        val expiresAt: Instant
    )
}
