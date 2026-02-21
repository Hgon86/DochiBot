package com.dochibot.domain.repository

import com.dochibot.domain.entity.ChatMessage
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * 채팅 메시지 Entity Repository.
 */
@Repository
interface ChatMessageRepository : CoroutineCrudRepository<ChatMessage, UUID> {

    /**
     * 세션별 메시지 목록 조회 (생성순).
     * @param chatSessionId 세션 ID
     * @return 메시지 Flow (생성시각 오름차순)
     */
    fun findByChatSessionIdOrderByCreatedAtAsc(chatSessionId: UUID): Flow<ChatMessage>
}
