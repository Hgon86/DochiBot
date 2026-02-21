package com.dochibot.domain.entity

import com.dochibot.common.util.id.Uuid7Generator
import com.dochibot.domain.enums.ChatRole
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * 채팅 메시지 Entity.
 *
 * @property id 메시지 UUID (앱에서 UUIDv7로 생성)
 * @property chatSessionId 소속 세션 ID (FK -> chat_sessions.id, CASCADE 삭제)
 * @property role 메시지 역할 (USER/ASSISTANT)
 * @property content 메시지 콘텐츠
 * @property citationsJson AI 응답의 근거 정보 (JSON, nullable)
 * @property createdAt 생성 시각 (Auditing 자동 채움)
 */
@Table("chat_messages")
data class ChatMessage(
    @get:JvmName("getMessageId")
    @field:Id
    val id: UUID = Uuid7Generator.create(),
    val chatSessionId: UUID,
    val role: ChatRole,
    val content: String,
    val citationsJson: String? = null,
    @CreatedDate
    val createdAt: Instant? = null
) : BasePersistableUuidEntity() {
    override fun getId(): UUID = id

    companion object {
        fun new(
            chatSessionId: UUID,
            role: ChatRole,
            content: String,
            citationsJson: String? = null
        ): ChatMessage {
            val entity = ChatMessage(
                chatSessionId = chatSessionId,
                role = role,
                content = content,
                citationsJson = citationsJson
            )

            entity.markAsNew()
            return entity
        }
    }
}
