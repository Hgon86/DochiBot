package com.dochibot.feature.document.application

import com.dochibot.common.config.DochibotAiProperties
import com.dochibot.common.exception.CommonErrorCode
import com.dochibot.common.exception.DochiException
import com.dochibot.common.storage.config.S3Properties
import com.dochibot.common.storage.service.S3Service
import com.dochibot.domain.entity.Document
import com.dochibot.domain.entity.DocumentIngestionJob
import com.dochibot.domain.enums.DocumentStatus
import com.dochibot.domain.repository.DocumentIngestionJobRepository
import com.dochibot.domain.repository.DocumentRepository
import com.dochibot.feature.document.dto.FinalizeDocumentUploadRequest
import com.dochibot.feature.document.dto.FinalizeDocumentUploadResponse
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.util.*

@Service
class FinalizeDocumentUploadUseCase(
    private val documentRepository: DocumentRepository,
    private val documentIngestionJobRepository: DocumentIngestionJobRepository,
    private val s3Service: S3Service,
    private val s3Properties: S3Properties,
    private val dochibotAiProperties: DochibotAiProperties,
    private val transactionalOperator: TransactionalOperator,
) {
    /**
     * 업로드된 오브젝트를 문서로 "확정"하고, 인덱싱 작업을 생성한다.
     *
     * @param jwt 인증 주체(JWT)
     * @param request 업로드 확정 요청
     * @return 생성된 문서/작업 정보
     */
    suspend fun execute(jwt: Jwt, request: FinalizeDocumentUploadRequest): FinalizeDocumentUploadResponse {
        return transactionalOperator.executeAndAwait {
            val documentId = request.documentId

            val parsed = s3Service.parseStorageUri(request.storageUri)
            if (parsed.bucket != s3Properties.bucket) {
                throw DochiException(CommonErrorCode.BAD_REQUEST, "Invalid storageUri bucket")
            }

            validateObjectKeyRule(documentId = documentId, key = parsed.key)

            val createdByUserId = runCatching { UUID.fromString(jwt.subject) }
                .getOrElse {
                    throw DochiException(CommonErrorCode.AUTH_INVALID_TOKEN, "Invalid subject format", it)
                }
            val document = Document.new(
                id = documentId,
                title = request.title,
                sourceType = request.sourceType,
                originalFilename = request.originalFilename,
                storageUri = request.storageUri,
                status = DocumentStatus.PENDING,
                createdByUserId = createdByUserId,
            )

            val saved = try {
                documentRepository.save(document)
            } catch (e: DuplicateKeyException) {
                throw DochiException(
                    CommonErrorCode.CONFLICT,
                    "Document already exists: documentId=$documentId",
                    e
                )
            }
            val job = documentIngestionJobRepository.save(
                DocumentIngestionJob.new(
                    documentId = saved.id,
                    embeddingModel = dochibotAiProperties.embedding.model,
                    embeddingDims = dochibotAiProperties.embedding.dims,
                )
            )

            FinalizeDocumentUploadResponse(
                documentId = saved.id,
                status = saved.status,
                ingestionJobId = job.id,
            )
        }
    }

    private fun validateObjectKeyRule(documentId: UUID, key: String) {
        val normalized = key.trimStart('/')
        val expectedPrefix = "${documentId}_"

        // yyyy/MM/{documentId}_...
        val ok = Regex("^\\d{4}/\\d{2}/" + Regex.escape(expectedPrefix) + ".+").matches(normalized)
        if (!ok) {
            throw DochiException(CommonErrorCode.BAD_REQUEST, "Invalid object key rule")
        }
    }
}
