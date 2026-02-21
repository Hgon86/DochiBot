package com.dochibot.domain.repository

import com.dochibot.domain.entity.User
import com.dochibot.domain.enums.AuthProvider
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * 사용자 Entity Repository.
 */
@Repository
interface UserRepository : CoroutineCrudRepository<User, UUID> {

    /**
     * 사용자명으로 사용자 조회.
     * @param username 로그인 아이디
     * @return 사용자 (존재하지 않으면 null)
     */
    suspend fun findByUsername(username: String): User?

    /**
     * OAuth provider + providerId로 사용자 조회.
     * OAuth 로그인 시 기존 사용자 확인용.
     * @param provider 인증 제공자
     * @param providerId OAuth Provider의 사용자 ID
     * @return 사용자 (존재하지 않으면 null)
     */
    suspend fun findByProviderAndProviderId(
        provider: AuthProvider,
        providerId: String
    ): User?

    /**
     * 이메일로 OAuth 사용자 조회 (provider가 null이 아닌 경우).
     * OAuth 콜백 시 이메일 기반으로 기존 사용자 검색.
     * @param email OAuth에서 가져온 이메일
     * @return 사용자 (존재하지 않으면 null)
     */
    suspend fun findByUsernameAndProviderNot(
        username: String,
        provider: AuthProvider
    ): User?

    /**
     * 사용자명 존재 여부 확인.
     * @param username 로그인 아이디
     * @return 존재 여부
     */
    suspend fun existsByUsername(username: String): Boolean
}
