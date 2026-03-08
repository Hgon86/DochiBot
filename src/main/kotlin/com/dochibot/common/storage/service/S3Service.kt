package com.dochibot.common.storage.service

import com.dochibot.common.exception.CommonErrorCode
import com.dochibot.common.exception.DochiException
import com.dochibot.common.storage.config.S3Properties
import com.dochibot.common.storage.util.ParsedS3Uri
import com.dochibot.common.storage.util.S3StorageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest

/**
 * S3 호환 스토리지(SeaweedFS S3 gateway 등)용 Presigned URL 발급 유틸리티.
 */
@Service
class S3Service(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val props: S3Properties,
) {
    private val log = KotlinLogging.logger {}

    /**
     * 업로드(PUT)용 presigned URL을 발급한다.
     *
     * @param bucket 버킷
     * @param key 오브젝트 key
     * @param contentType 업로드 Content-Type
     * @param expiresInSeconds 만료 시간(초)
     */
    fun createPresignedPut(
        bucket: String,
        key: String,
        contentType: String,
        expiresInSeconds: Long = props.presignedUrlExpirationSeconds,
    ): PresignedPutObjectRequest {
        log.info {
            "Presign PUT: bucket=$bucket, key=$key, contentType=$contentType, expiresInSeconds=$expiresInSeconds"
        }
        return s3Presigner.presignPutObject { request ->
            request.signatureDuration(Duration.ofSeconds(expiresInSeconds))
                .putObjectRequest {
                    it.bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                }
        }
    }

    /**
     * 다운로드(GET)용 presigned URL을 발급한다.
     *
     * @param bucket 버킷
     * @param key 오브젝트 key
     * @param responseFilename (선택) 다운로드 파일명
     * @param expiresInSeconds 만료 시간(초)
     */
    fun createPresignedGet(
        bucket: String,
        key: String,
        responseFilename: String?,
        expiresInSeconds: Long = props.presignedUrlExpirationSeconds,
    ): PresignedGetObjectRequest {
        log.info {
            "Presign GET: bucket=$bucket, key=$key, hasResponseFilename=${!responseFilename.isNullOrBlank()}, expiresInSeconds=$expiresInSeconds"
        }

        val contentDisposition = responseFilename
            ?.takeIf { it.isNotBlank() }
            ?.let { S3StorageUtils.buildAttachmentContentDisposition(it) }

        return s3Presigner.presignGetObject { request ->
            request.signatureDuration(Duration.ofSeconds(expiresInSeconds))
                .getObjectRequest {
                    it.bucket(bucket)
                        .key(key)
                    if (contentDisposition != null) {
                        it.responseContentDisposition(contentDisposition)
                    }
                }
        }
    }

    /**
     * 오브젝트를 삭제한다.
     *
     * @param bucket 버킷
     * @param key 오브젝트 key
     */
    fun deleteObject(bucket: String, key: String) {
        log.info { "Delete object: bucket=$bucket, key=$key" }

        try {
            s3Client.deleteObject { request ->
                request.bucket(bucket)
                    .key(key)
            }
        } catch (_: NoSuchKeyException) {
            log.info { "Skip deleting missing object: bucket=$bucket, key=$key" }
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) {
                log.info { "Skip deleting missing object via S3Exception: bucket=$bucket, key=$key" }
                return
            }
            throw e
        }
    }

    /**
     * 문서 업로드 오브젝트 key 규칙을 생성한다.
     *
     * 형식: yyyy/MM/{documentId}_{filename}
     */
    fun buildDocumentObjectKey(
        documentId: UUID,
        originalFilename: String,
        now: Instant = S3StorageUtils.nowUtc(),
    ): String {
        val key = S3StorageUtils.buildDocumentObjectKey(
            documentId = documentId,
            originalFilename = originalFilename,
            now = now,
        )
        log.debug { "Built document object key: documentId=$documentId, key=$key" }
        return key
    }

    /**
     * S3 저장 URI를 생성한다.
     *
     * 형식: s3://{bucket}/{key}
     */
    fun toStorageUri(bucket: String, key: String): String {
        return S3StorageUtils.toStorageUri(bucket = bucket, key = key)
    }

    /**
     * S3 저장 URI를 파싱한다.
     *
     * 형식: s3://{bucket}/{key}
     */
    fun parseStorageUri(storageUri: String): ParsedS3Uri {
        return try {
            S3StorageUtils.parseStorageUri(storageUri).also {
                log.debug { "Parsed storageUri: bucket=${it.bucket}, key=${it.key}" }
            }
        } catch (e: DochiException) {
            if (e.errorCode == CommonErrorCode.BAD_REQUEST && e.detail == "Invalid storageUri") {
                log.warn(e) { "Failed to parse storageUri: $storageUri" }
            }
            throw e
        }
    }
}
