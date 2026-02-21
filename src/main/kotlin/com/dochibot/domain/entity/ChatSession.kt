package com.dochibot.domain.entity

import com.dochibot.common.util.id.Uuid7Generator
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * 채팅 세션 Entity.
 * UI가 유지하는 세션 키를 서버에서 추적.
 *
 * @property id 세션 UUID (앱에서 UUIDv7로 생성)
 * @property externalSessionKey UI에서 사용하는 외부 세션 키 (UNIQUE)
 * @property ownerUserId 세션 소유자 사용자 ID (nullable)
 * @property createdAt 생성 시각 (Auditing 자동 채움)
 */
@Table("chat_sessions")
data class ChatSession(
    @get:JvmName("getSessionId")
    @field:Id
    val id: UUID = Uuid7Generator.create(),
    val externalSessionKey: String,
    val ownerUserId: UUID? = null,
    @CreatedDate
    val createdAt: Instant? = null
) : BasePersistableUuidEntity() {
    override fun getId(): UUID = id

    companion object {
        fun new(
            externalSessionKey: String,
            ownerUserId: UUID? = null
        ): ChatSession {
            val entity = ChatSession(
                externalSessionKey = externalSessionKey,
                ownerUserId = ownerUserId
            )

            entity.markAsNew()
            return entity
        }
    }
}
