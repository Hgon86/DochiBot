package com.dochibot.feature.ingestionjob.application

import com.dochibot.common.config.DochibotAiProperties
import com.dochibot.domain.entity.DocumentIngestionJob
import com.dochibot.domain.enums.DocumentStatus
import com.dochibot.domain.repository.DocumentRepository
import com.dochibot.feature.ingestionjob.exception.NonRetryableIngestionException
import com.dochibot.feature.ingestionjob.repository.DocumentIndexWriter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Service

/**
 * 문서 인제션 처리 파이프라인.
 *
 * @property ingestionJobService 작업 선점/상태 관리
 * @property documentRepository 문서 Repository
 * @property documentContentLoader 원본 로더
 * @property documentTextExtractor 텍스트 추출기
 * @property textChunkingService 청킹 서비스
 * @property documentIndexWriter sections/chunks 저장소
 * @property embeddingModel 임베딩 모델
 * @property dochibotAiProperties AI 설정
 */
@Service
class DocumentIngestionProcessor(
    private val ingestionJobService: IngestionJobService,
    private val documentRepository: DocumentRepository,
    private val documentContentLoader: DocumentContentLoader,
    private val documentTextExtractor: DocumentTextExtractor,
    private val textChunkingService: TextChunkingService,
    private val documentIndexWriter: DocumentIndexWriter,
    private val embeddingModel: EmbeddingModel,
    private val dochibotAiProperties: DochibotAiProperties,
) {
    private val log = KotlinLogging.logger {}

    /**
     * 최대 지정 개수만큼 작업을 처리한다.
     *
     * @param maxJobs 최대 처리 개수
     */
    suspend fun processBatch(maxJobs: Int) {
        var processed = 0
        while (processed < maxJobs) {
            val job = ingestionJobService.claimNextQueuedJob() ?: break
            processed += 1
            runCatching { process(job) }
                .onFailure { ex ->
                    if (ex is CancellationException) {
                        throw ex
                    }
                    log.error(ex) { "Ingestion failed: jobId=${job.id}, documentId=${job.documentId}" }
                    val message = ex.message?.take(500) ?: "Unknown error"
                    val now = Instant.now()
                    val shouldRetry = ex !is NonRetryableIngestionException && ingestionJobService.canRetry(job)
                    if (shouldRetry) {
                        val nextRunAt = now.plus(calculateBackoff(job.attemptCount + 1))
                        ingestionJobService.scheduleRetry(job, message, nextRunAt)
                    } else {
                        ingestionJobService.markFailed(job.id, message, now)
                    }
                    val document = documentRepository.findById(job.documentId)
                    if (document != null) {
                        documentRepository.save(
                            document.copy(
                                status = if (shouldRetry) DocumentStatus.PROCESSING else DocumentStatus.FAILED,
                                errorMessage = message,
                            )
                        )
                    }
                }
        }
    }

    private suspend fun process(job: DocumentIngestionJob) {
        val document = documentRepository.findById(job.documentId)
            ?: throw IllegalStateException("문서를 찾을 수 없습니다: documentId=${job.documentId}")

        if (document.storageUri.isNullOrBlank()) {
            throw IllegalStateException("문서 storageUri가 비어 있습니다: documentId=${job.documentId}")
        }

        val embeddingModelName = job.embeddingModel ?: dochibotAiProperties.embedding.model
        if (job.embeddingModel != null && job.embeddingModel != dochibotAiProperties.embedding.model) {
            throw NonRetryableIngestionException(
                "임베딩 모델이 변경되었습니다. 기존=${job.embeddingModel}, 현재=${dochibotAiProperties.embedding.model}"
            )
        }

        if (job.embeddingDims != null && job.embeddingDims != dochibotAiProperties.embedding.dims) {
            throw NonRetryableIngestionException(
                "임베딩 차원이 변경되었습니다. 기존=${job.embeddingDims}, 현재=${dochibotAiProperties.embedding.dims}"
            )
        }

        documentRepository.save(
            document.copy(
                status = DocumentStatus.PROCESSING,
                errorMessage = null,
            )
        )

        val content = documentContentLoader.load(document.storageUri)
        val pages = withContext(Dispatchers.IO) {
            documentTextExtractor.extract(document, content)
        }
        val chunks = textChunkingService.chunk(pages)
        if (chunks.isEmpty()) {
            throw IllegalStateException("추출된 텍스트가 없습니다: documentId=${job.documentId}")
        }

        val now = Instant.now()

        // 재시도/재처리 시 중복 적재를 방지하기 위해 기존 인덱스를 먼저 제거한다.
        documentIndexWriter.deleteByDocumentId(job.documentId)

        // 최소 구현: 문서당 루트 섹션 1개 + 청크 저장
        val rootSectionId = documentIndexWriter.insertRootSection(document)

        val texts = chunks.map { it.text }
        val allEmbeddings = mutableListOf<FloatArray>()

        // 임베딩은 외부 호출일 수 있으므로 IO 컨텍스트에서 실행한다.
        withContext(Dispatchers.IO) {
            texts.chunked(64).forEach { batch ->
                allEmbeddings += embeddingModel.embed(batch)
            }
        }

        val dims = allEmbeddings.firstOrNull()?.size
            ?: throw IllegalStateException("임베딩 결과가 비어 있습니다: documentId=${job.documentId}")
        if (dims != dochibotAiProperties.embedding.dims) {
            throw NonRetryableIngestionException(
                "임베딩 차원이 DB 스키마와 다릅니다. expected=${dochibotAiProperties.embedding.dims}, actual=$dims"
            )
        }
        if (allEmbeddings.size != chunks.size) {
            throw IllegalStateException("임베딩 개수가 청크 수와 다릅니다: chunks=${chunks.size}, embeddings=${allEmbeddings.size}")
        }

        // Gate(dense)를 위해 최소 구현으로 섹션 임베딩을 함께 채운다.
        // Phase 1에서는 루트 섹션 1개만 있으므로, 청크 임베딩 평균 벡터를 섹션 임베딩으로 사용한다.
        val sectionEmbedding = averageEmbedding(allEmbeddings)
        documentIndexWriter.updateSectionEmbedding(rootSectionId, sectionEmbedding)

        documentIndexWriter.insertChunks(
            documentId = job.documentId,
            sectionId = rootSectionId,
            chunks = chunks,
            embeddings = allEmbeddings,
        )

        ingestionJobService.markSucceeded(
            jobId = job.id,
            chunkCount = chunks.size,
            embeddingModel = embeddingModelName,
            embeddingDims = dims,
            finishedAt = now,
        )

        documentRepository.save(
            document.copy(
                status = DocumentStatus.COMPLETED,
                errorMessage = null,
            )
        )
    }

    private fun calculateBackoff(attempt: Int): Duration {
        val seconds = when (attempt) {
            1 -> 60L
            2 -> 300L
            else -> 1800L
        }
        return Duration.ofSeconds(seconds)
    }

    private fun averageEmbedding(embeddings: List<FloatArray>): FloatArray {
        require(embeddings.isNotEmpty()) { "embeddings must not be empty" }
        val dims = embeddings.first().size
        val acc = FloatArray(dims)
        for (e in embeddings) {
            require(e.size == dims) { "embedding dims mismatch" }
            for (i in 0 until dims) {
                acc[i] += e[i]
            }
        }
        val n = embeddings.size.toFloat()
        for (i in 0 until dims) {
            acc[i] /= n
        }
        return acc
    }
}
