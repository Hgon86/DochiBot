package com.dochibot.domain.repository

import com.dochibot.domain.entity.Document
import com.dochibot.domain.enums.DocumentStatus
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * 문서 Entity Repository.
 */
@Repository
interface DocumentRepository : CoroutineCrudRepository<Document, UUID> {

    /**
     * 상태별 문서 목록 조회.
     * @param status 문서 처리 상태
     * @return 문서 Flow
     */
    fun findByStatus(status: DocumentStatus): Flow<Document>

    /**
     * 문서 목록을 최신순으로 페이지 단위 조회한다.
     *
     * @param limit 조회 개수
     * @param offset 오프셋
     */
    @Query("""
        select *
        from documents
        order by created_at desc
        limit :limit offset :offset
    """)
    fun findPage(limit: Int, offset: Long): Flow<Document>

    /**
     * 상태별 문서 목록을 최신순으로 페이지 단위 조회한다.
     *
     * @param status 문서 처리 상태
     * @param limit 조회 개수
     * @param offset 오프셋
     */
    @Query("""
        select *
        from documents
        where status = :status
        order by created_at desc
        limit :limit offset :offset
    """)
    fun findPageByStatus(status: String, limit: Int, offset: Long): Flow<Document>
}
