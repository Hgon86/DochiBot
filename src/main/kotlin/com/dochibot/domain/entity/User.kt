package com.dochibot.domain.entity

import com.dochibot.common.util.id.Uuid7Generator
import com.dochibot.domain.enums.AuthProvider
import com.dochibot.domain.enums.UserRole
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.*

/**
 * 로그인 사용자 Entity.
 *
 * @property id 사용자 UUID (앱에서 UUIDv7로 생성)
 * @property username 로그인 아이디 (UNIQUE)
 * @property passwordHash BCrypt 해시된 비밀번호 (CREDENTIALS 인증 시 필수, OAuth 시 NULL)
 * @property role 사용자 역할 (ADMIN/USER)
 * @property provider 인증 제공자 (CREDENTIALS/GOOGLE/GITHUB)
 * @property providerId OAuth Provider의 사용자 ID (CREDENTIALS 시 NULL)
 * @property isActive 계정 활성화 여부
 * @property createdAt 생성 시각 (Auditing 자동 채움)
 * @property updatedAt 수정 시각 (Auditing 자동 채움)
 */
@Table("users")
data class User(
    @get:JvmName("getUserId")
    @field:Id
    val id: UUID = Uuid7Generator.create(),
    val username: String,
    val passwordHash: String? = null,
    val role: UserRole = UserRole.USER,
    val provider: AuthProvider = AuthProvider.CREDENTIALS,
    val providerId: String? = null,
    val isActive: Boolean = true,
    @CreatedDate
    val createdAt: Instant? = null,
    @LastModifiedDate
    val updatedAt: Instant? = null
) : BasePersistableUuidEntity() {

    /**
     * OAuth 사용자인지 확인.
     */
    fun isOAuthUser(): Boolean = provider != AuthProvider.CREDENTIALS

    override fun getId(): UUID = id

    companion object {
        fun new(
            username: String,
            passwordHash: String? = null,
            role: UserRole = UserRole.USER,
            provider: AuthProvider = AuthProvider.CREDENTIALS,
            providerId: String? = null,
            isActive: Boolean = true
        ): User {
            val entity = User(
                username = username,
                passwordHash = passwordHash,
                role = role,
                provider = provider,
                providerId = providerId,
                isActive = isActive
            )

            entity.markAsNew()
            return entity
        }
    }
}
