package com.dochibot.feature.document.application

import com.dochibot.common.config.DochibotAiProperties
import com.dochibot.common.exception.CommonErrorCode
import com.dochibot.common.exception.DochiException
import com.dochibot.domain.entity.DocumentIngestionJob
import com.dochibot.domain.enums.DocumentStatus
import com.dochibot.domain.repository.DocumentIngestionJobRepository
import com.dochibot.domain.repository.DocumentRepository
import com.dochibot.feature.document.dto.ReindexDocumentResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.util.UUID

@Service
class ReindexDocumentUseCase(
    private val documentRepository: DocumentRepository,
    private val documentIngestionJobRepository: DocumentIngestionJobRepository,
    private val dochibotAiProperties: DochibotAiProperties,
    private val transactionalOperator: TransactionalOperator,
) {
    /**
     * 문서 재인덱싱 작업을 생성한다.
     *
     * @param documentId 문서 ID
     * @return 생성된 작업 정보
     */
    suspend fun execute(documentId: UUID): ReindexDocumentResponse {
        return transactionalOperator.executeAndAwait {
            val document = documentRepository.findById(documentId)
                ?: throw DochiException(CommonErrorCode.NOT_FOUND, "Document not found: documentId=$documentId")

            if (document.status == DocumentStatus.FAILED || document.status == DocumentStatus.COMPLETED) {
                documentRepository.save(
                    document.copy(
                        status = DocumentStatus.PENDING,
                        errorMessage = null,
                    )
                )
            }

            val job = documentIngestionJobRepository.save(
                DocumentIngestionJob.new(
                    documentId = documentId,
                    embeddingModel = dochibotAiProperties.embedding.model,
                    embeddingDims = dochibotAiProperties.embedding.dims,
                )
            )

            ReindexDocumentResponse(
                jobId = job.id,
                status = job.status,
            )
        }
    }
}
