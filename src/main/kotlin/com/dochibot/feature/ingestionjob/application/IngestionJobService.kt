package com.dochibot.feature.ingestionjob.application

import com.dochibot.domain.entity.DocumentIngestionJob
import com.dochibot.domain.enums.IngestionJobStatus
import com.dochibot.domain.repository.DocumentIngestionJobRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * 인덱싱 작업 선점/상태 전이를 담당하는 서비스.
 *
 * @property documentIngestionJobRepository 인덱싱 작업 Repository
 */
@Service
class IngestionJobService(
    private val documentIngestionJobRepository: DocumentIngestionJobRepository,
) {
    /**
     * 대기(QUEUED) 작업 1건을 선점하여 RUNNING으로 전환한다.
     * @return 선점된 작업 (없으면 null)
     */
    suspend fun claimNextQueuedJob(): DocumentIngestionJob? {
        val candidate = documentIngestionJobRepository.findNextQueuedJob()
            ?: return null
        val startedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
        documentIngestionJobRepository.claimJob(candidate.id, startedAt)

        val claimed = documentIngestionJobRepository.findById(candidate.id)
            ?: return null

        return if (claimed.status == IngestionJobStatus.RUNNING && claimed.startedAt == startedAt) {
            claimed
        } else {
            null
        }
    }

    /**
     * 작업을 성공 상태로 업데이트한다.
     *
     * @param jobId 작업 ID
     * @param chunkCount 처리된 청크 수
     * @param embeddingModel 임베딩 모델명
     * @param embeddingDims 임베딩 차원(dims)
     * @param finishedAt 종료 시각
     */
    suspend fun markSucceeded(
        jobId: UUID,
        chunkCount: Int,
        embeddingModel: String,
        embeddingDims: Int,
        finishedAt: Instant,
    ) {
        documentIngestionJobRepository.updateJobResult(
            id = jobId,
            status = IngestionJobStatus.SUCCEEDED.name,
            chunkCount = chunkCount,
            embeddingModel = embeddingModel,
            embeddingDims = embeddingDims,
            finishedAt = finishedAt,
            errorMessage = null,
        )
    }

    /**
     * 작업을 실패 상태로 업데이트한다.
     *
     * @param jobId 작업 ID
     * @param errorMessage 실패 메시지
     * @param finishedAt 종료 시각
     */
    suspend fun markFailed(
        jobId: UUID,
        errorMessage: String,
        finishedAt: Instant,
    ) {
        documentIngestionJobRepository.updateJobResult(
            id = jobId,
            status = IngestionJobStatus.FAILED.name,
            chunkCount = null,
            embeddingModel = null,
            embeddingDims = null,
            finishedAt = finishedAt,
            errorMessage = errorMessage,
        )
    }

    /**
     * 재시도 가능한 실패를 QUEUED로 되돌리고 backoff를 설정한다.
     *
     * @param job 재시도 대상 작업
     * @param errorMessage 실패 메시지
     * @param nextRunAt 다음 실행 시각
     */
    suspend fun scheduleRetry(
        job: DocumentIngestionJob,
        errorMessage: String,
        nextRunAt: Instant,
    ) {
        documentIngestionJobRepository.scheduleRetry(
            id = job.id,
            status = IngestionJobStatus.QUEUED.name,
            attemptCount = job.attemptCount + 1,
            nextRunAt = nextRunAt,
            errorMessage = errorMessage,
        )
    }

    /**
     * 재시도 가능 여부를 판단한다.
     *
     * @param job 대상 작업
     * @return 재시도 가능 여부
     */
    fun canRetry(job: DocumentIngestionJob): Boolean {
        return job.attemptCount < job.maxAttempts
    }
}
