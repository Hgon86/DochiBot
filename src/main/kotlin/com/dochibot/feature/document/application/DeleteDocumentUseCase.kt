package com.dochibot.feature.document.application

import com.dochibot.common.exception.CommonErrorCode
import com.dochibot.common.exception.DochiException
import com.dochibot.common.storage.service.S3Service
import com.dochibot.domain.enums.DocumentStatus
import com.dochibot.domain.repository.DocumentRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DeleteDocumentUseCase(
    private val documentRepository: DocumentRepository,
    private val s3Service: S3Service,
) {
    /**
     * 문서 메타데이터와 원본 오브젝트를 삭제한다.
     *
     * @param documentId 문서 ID
     */
    suspend fun execute(documentId: UUID) {
        val document = documentRepository.findById(documentId)
            ?: throw DochiException(CommonErrorCode.NOT_FOUND, "Document not found: documentId=$documentId")

        if (document.status == DocumentStatus.PROCESSING) {
            throw DochiException(
                CommonErrorCode.CONFLICT,
                "Cannot delete processing document: documentId=$documentId",
            )
        }

        document.storageUri
            ?.takeIf { it.isNotBlank() }
            ?.let { storageUri ->
                val parsed = s3Service.parseStorageUri(storageUri)
                s3Service.deleteObject(bucket = parsed.bucket, key = parsed.key)
            }

        documentRepository.delete(document)
    }
}
