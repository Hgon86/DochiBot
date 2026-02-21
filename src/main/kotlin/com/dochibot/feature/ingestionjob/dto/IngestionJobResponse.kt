package com.dochibot.feature.ingestionjob.dto

import com.dochibot.domain.entity.DocumentIngestionJob
import com.dochibot.domain.enums.IngestionJobStatus
import java.time.Instant
import java.util.UUID

data class IngestionJobResponse(
    val id: UUID,
    val documentId: UUID,
    val status: IngestionJobStatus,
    val chunkCount: Int? = null,
    val embeddingModel: String? = null,
    val embeddingDims: Int? = null,
    val attemptCount: Int,
    val maxAttempts: Int,
    val nextRunAt: Instant? = null,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val errorMessage: String? = null,
    val createdAt: Instant? = null,
) {
    companion object {
        fun from(job: DocumentIngestionJob): IngestionJobResponse {
            return IngestionJobResponse(
                id = job.id,
                documentId = job.documentId,
                status = job.status,
                chunkCount = job.chunkCount,
                embeddingModel = job.embeddingModel,
                embeddingDims = job.embeddingDims,
                attemptCount = job.attemptCount,
                maxAttempts = job.maxAttempts,
                nextRunAt = job.nextRunAt,
                startedAt = job.startedAt,
                finishedAt = job.finishedAt,
                // 외부 API 응답에 내부 오류 메시지를 노출하지 않는다.
                errorMessage = null,
                createdAt = job.createdAt,
            )
        }
    }
}
