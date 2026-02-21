package com.dochibot.domain.repository

import com.dochibot.domain.entity.ChatSession
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * 채팅 세션 Entity Repository.
 */
@Repository
interface ChatSessionRepository : CoroutineCrudRepository<ChatSession, UUID> {

    /**
     * 외부 세션 키로 세션 조회.
     * @param externalSessionKey UI에서 사용하는 세션 키
     * @return 세션 (존재하지 않으면 null)
     */
    suspend fun findByExternalSessionKey(externalSessionKey: String): ChatSession?

    /**
     * 외부 세션 키 존재 여부 확인.
     * @param externalSessionKey UI에서 사용하는 세션 키
     * @return 존재 여부
     */
    suspend fun existsByExternalSessionKey(externalSessionKey: String): Boolean
}
