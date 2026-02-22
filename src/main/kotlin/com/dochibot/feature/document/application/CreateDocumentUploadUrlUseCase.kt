package com.dochibot.feature.document.application

import com.dochibot.common.exception.CommonErrorCode
import com.dochibot.common.exception.DochiException
import com.dochibot.common.storage.config.S3Properties
import com.dochibot.common.storage.service.S3Service
import com.dochibot.common.util.id.Uuid7Generator
import com.dochibot.feature.document.dto.CreateDocumentUploadUrlRequest
import com.dochibot.feature.document.dto.CreateDocumentUploadUrlResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CreateDocumentUploadUrlUseCase(
    private val s3Service: S3Service,
    private val s3Properties: S3Properties,
) {

    /**
     * 문서 업로드용 presigned PUT URL을 발급한다.
     *
     * @param request 업로드 URL 발급 요청
     * @return presigned URL 및 저장 위치 정보
     */
    fun execute(request: CreateDocumentUploadUrlRequest): CreateDocumentUploadUrlResponse {
        validateSupportedFormat(request.originalFilename, request.contentType)

        val documentId: UUID = Uuid7Generator.create()
        val bucket = s3Properties.bucket
        val key = s3Service.buildDocumentObjectKey(documentId, request.originalFilename)
        val presigned = s3Service.createPresignedPut(
            bucket = bucket,
            key = key,
            contentType = request.contentType,
        )

        val requiredHeaders = mapOf(
            "Content-Type" to request.contentType,
        )

        return CreateDocumentUploadUrlResponse(
            documentId = documentId,
            bucket = bucket,
            key = key,
            storageUri = s3Service.toStorageUri(bucket = bucket, key = key),
            uploadUrl = presigned.url().toString(),
            method = "PUT",
            expiresInSeconds = s3Properties.presignedUrlExpirationSeconds,
            requiredHeaders = requiredHeaders,
        )
    }

    private fun validateSupportedFormat(originalFilename: String, contentType: String) {
        if (!DocumentUploadPolicy.isSupportedUpload(originalFilename, contentType)) {
            throw DochiException(
                CommonErrorCode.BAD_REQUEST,
                DocumentUploadPolicy.UNSUPPORTED_FORMAT_MESSAGE
            )
        }
    }
}
