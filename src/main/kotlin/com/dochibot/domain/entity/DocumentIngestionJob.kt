package com.dochibot.domain.entity

import com.dochibot.common.util.id.Uuid7Generator
import com.dochibot.domain.enums.IngestionJobStatus
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * 문서 인덱싱 작업 Entity.
 * 재시도/실패 추적을 위한 작업 단위.
 *
 * @property id 작업 UUID (앱에서 UUIDv7로 생성)
 * @property documentId 참조 문서 ID (FK -> documents.id, CASCADE 삭제)
 * @property status 작업 상태 (QUEUED/RUNNING/SUCCEEDED/FAILED)
 * @property chunkCount 처리된 청크 수
 * @property embeddingModel 사용된 임베딩 모델명
 * @property embeddingDims 임베딩 차원(dims)
 * @property attemptCount 재시도 횟수
 * @property maxAttempts 최대 재시도 횟수
 * @property nextRunAt 다음 실행 시각(재시도 백오프)
 * @property startedAt 작업 시작 시각
 * @property finishedAt 작업 종료 시각
 * @property errorMessage 작업 실패 시 에러 메시지
 * @property createdAt 생성 시각 (Auditing 자동 채움)
 * @property updatedAt 수정 시각 (Auditing 자동 채움)
 */
@Table("document_ingestion_jobs")
data class DocumentIngestionJob(
    @get:JvmName("getJobId")
    @field:Id
    val id: UUID = Uuid7Generator.create(),
    val documentId: UUID,
    val status: IngestionJobStatus = IngestionJobStatus.QUEUED,
    val chunkCount: Int? = null,
    val embeddingModel: String? = null,
    val embeddingDims: Int? = null,
    val attemptCount: Int = 0,
    val maxAttempts: Int = 3,
    val nextRunAt: Instant? = null,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val errorMessage: String? = null,
    @CreatedDate
    val createdAt: Instant? = null,
    @LastModifiedDate
    val updatedAt: Instant? = null
) : BasePersistableUuidEntity() {
    override fun getId(): UUID = id

    companion object {
        fun new(
            documentId: UUID,
            status: IngestionJobStatus = IngestionJobStatus.QUEUED,
            chunkCount: Int? = null,
            embeddingModel: String? = null,
            embeddingDims: Int? = null,
            attemptCount: Int = 0,
            maxAttempts: Int = 3,
            nextRunAt: Instant? = null,
            startedAt: Instant? = null,
            finishedAt: Instant? = null,
            errorMessage: String? = null
        ): DocumentIngestionJob {
            val entity = DocumentIngestionJob(
                documentId = documentId,
                status = status,
                chunkCount = chunkCount,
                embeddingModel = embeddingModel,
                embeddingDims = embeddingDims,
                attemptCount = attemptCount,
                maxAttempts = maxAttempts,
                nextRunAt = nextRunAt,
                startedAt = startedAt,
                finishedAt = finishedAt,
                errorMessage = errorMessage
            )

            entity.markAsNew()
            return entity
        }
    }
}
