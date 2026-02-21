package com.dochibot.feature.document.application

import com.dochibot.common.exception.CommonErrorCode
import com.dochibot.common.exception.DochiException
import com.dochibot.common.storage.config.S3Properties
import com.dochibot.common.storage.service.S3Service
import com.dochibot.domain.repository.DocumentRepository
import com.dochibot.feature.document.dto.CreateDocumentDownloadUrlResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CreateDocumentDownloadUrlUseCase(
    private val documentRepository: DocumentRepository,
    private val s3Service: S3Service,
    private val s3Properties: S3Properties,
) {
    /**
     * 문서 다운로드용 presigned GET URL을 발급한다.
     *
     * @param documentId 문서 ID
     * @param filename (옵션) 응답 파일명
     * @return 다운로드 URL
     */
    suspend fun execute(documentId: UUID, filename: String?): CreateDocumentDownloadUrlResponse {
        val document = documentRepository.findById(documentId)
            ?: throw DochiException(CommonErrorCode.NOT_FOUND, "Document not found: documentId=$documentId")

        val storageUri = document.storageUri?.takeIf { it.isNotBlank() }
            ?: throw DochiException(CommonErrorCode.BAD_REQUEST, "Document has no storageUri")

        val parsed = s3Service.parseStorageUri(storageUri)
        if (parsed.bucket != s3Properties.bucket) {
            throw DochiException(CommonErrorCode.BAD_REQUEST, "Invalid storageUri bucket")
        }

        val presigned = s3Service.createPresignedGet(
            bucket = parsed.bucket,
            key = parsed.key,
            responseFilename = filename?.takeIf { it.isNotBlank() } ?: document.originalFilename,
        )

        return CreateDocumentDownloadUrlResponse(
            downloadUrl = presigned.url().toString(),
            expiresInSeconds = s3Properties.presignedUrlExpirationSeconds,
        )
    }
}
