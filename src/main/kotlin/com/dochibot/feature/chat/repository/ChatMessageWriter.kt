package com.dochibot.feature.chat.repository

import com.dochibot.domain.entity.ChatMessage
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * chat_messages 저장 전용 쓰기 리포지토리.
 *
 * citations_json(jsonb) 컬럼에 문자열 JSON을 명시적으로 캐스팅해 저장한다.
 */
@Repository
class ChatMessageWriter(
    private val databaseClient: DatabaseClient,
) {
    suspend fun insert(message: ChatMessage) {
        var spec = databaseClient.sql(
            """
            insert into chat_messages (id, chat_session_id, role, content, citations_json, created_at)
            values (:id, :chatSessionId, :role, :content, (:citationsJson)::jsonb, :createdAt)
            """.trimIndent(),
        )
            .bind("id", message.id)
            .bind("chatSessionId", message.chatSessionId)
            .bind("role", message.role.name)
            .bind("content", message.content)
            .bind("createdAt", message.createdAt ?: Instant.now())

        spec = if (message.citationsJson == null) {
            spec.bindNull("citationsJson", String::class.java)
        } else {
            spec.bind("citationsJson", message.citationsJson)
        }

        spec.fetch().rowsUpdated().awaitSingle()
    }
}
