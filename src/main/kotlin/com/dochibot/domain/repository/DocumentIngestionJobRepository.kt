package com.dochibot.domain.repository

import com.dochibot.domain.entity.DocumentIngestionJob
import com.dochibot.domain.enums.IngestionJobStatus
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * 인덱싱 작업 Entity Repository.
 */
@Repository
interface DocumentIngestionJobRepository : CoroutineCrudRepository<DocumentIngestionJob, UUID> {

    /**
     * 문서별 인덱싱 작업 목록 조회.
     * @param documentId 문서 ID
     * @return 작업 Flow
     */
    fun findByDocumentId(documentId: UUID): Flow<DocumentIngestionJob>

    /**
     * 상태별 인덱싱 작업 목록 조회.
     * @param status 작업 상태
     * @return 작업 Flow
     */
    fun findByStatus(status: IngestionJobStatus): Flow<DocumentIngestionJob>

    /**
     * 가장 오래된 대기(QUEUED) 작업을 1건 조회한다.
     * @return 작업 (없으면 null)
     */
    @Query("""
        select *
        from document_ingestion_jobs
        where status = 'QUEUED'
          and (next_run_at is null or next_run_at <= now())
        order by created_at asc
        limit 1
    """)
    suspend fun findNextQueuedJob(): DocumentIngestionJob?

    /**
     * 대기(QUEUED) 작업을 RUNNING으로 선점한다.
     * @param id 작업 ID
     * @param startedAt 시작 시각
     * @return 업데이트된 행 수(1이면 성공)
     */
    @Query("""
        update document_ingestion_jobs
        set status = 'RUNNING',
            started_at = :startedAt,
            updated_at = now()
        where id = :id
          and status = 'QUEUED'
    """)
    suspend fun claimJob(id: UUID, startedAt: Instant): Int

    /**
     * 인덱싱 작업 처리 결과를 업데이트한다.
     * @param id 작업 ID
     * @param status 작업 상태
     * @param chunkCount 처리된 청크 수
     * @param embeddingModel 임베딩 모델명
     * @param finishedAt 종료 시각
     * @param errorMessage 실패 메시지
     * @return 업데이트된 행 수
     */
    @Query("""
        update document_ingestion_jobs
        set status = :status,
            chunk_count = :chunkCount,
            embedding_model = :embeddingModel,
            embedding_dims = :embeddingDims,
            finished_at = :finishedAt,
            error_message = :errorMessage,
            updated_at = now()
        where id = :id
    """)
    suspend fun updateJobResult(
        id: UUID,
        status: String,
        chunkCount: Int?,
        embeddingModel: String?,
        embeddingDims: Int?,
        finishedAt: Instant?,
        errorMessage: String?,
    ): Int

    /**
     * 재시도 가능한 실패 시 QUEUED로 되돌리고 backoff를 설정한다.
     *
     * @param id 작업 ID
     * @param status 작업 상태(보통 QUEUED)
     * @param attemptCount 현재 재시도 횟수
     * @param nextRunAt 다음 실행 시각
     * @param errorMessage 실패 메시지
     * @return 업데이트된 행 수
     */
    @Query("""
        update document_ingestion_jobs
        set status = :status,
            attempt_count = :attemptCount,
            next_run_at = :nextRunAt,
            error_message = :errorMessage,
            started_at = null,
            finished_at = null,
            chunk_count = null,
            embedding_model = null,
            embedding_dims = null,
            updated_at = now()
        where id = :id
    """)
    suspend fun scheduleRetry(
        id: UUID,
        status: String,
        attemptCount: Int,
        nextRunAt: Instant,
        errorMessage: String,
    ): Int

    /**
     * 인덱싱 작업 목록을 최신순으로 페이지 단위 조회한다.
     *
     * @param limit 조회 개수
     * @param offset 오프셋
     */
    @Query("""
        select *
        from document_ingestion_jobs
        order by created_at desc
        limit :limit offset :offset
    """)
    fun findPage(limit: Int, offset: Long): Flow<DocumentIngestionJob>

    /**
     * 문서별 인덱싱 작업 목록을 최신순으로 페이지 단위 조회한다.
     *
     * @param documentId 문서 ID
     * @param limit 조회 개수
     * @param offset 오프셋
     */
    @Query("""
        select *
        from document_ingestion_jobs
        where document_id = :documentId
        order by created_at desc
        limit :limit offset :offset
    """)
    fun findPageByDocumentId(documentId: UUID, limit: Int, offset: Long): Flow<DocumentIngestionJob>
}
