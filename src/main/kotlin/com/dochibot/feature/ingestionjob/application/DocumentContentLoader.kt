package com.dochibot.feature.ingestionjob.application

import com.dochibot.common.storage.service.S3Service
import com.dochibot.common.config.DochibotIngestionProperties
import com.dochibot.feature.ingestionjob.exception.NonRetryableIngestionException
import org.springframework.stereotype.Service
import kotlinx.coroutines.future.await
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest

/**
 * 문서 원본 로더.
 */
fun interface DocumentContentLoader {
    /**
     * storageUri를 기반으로 문서 원본을 로딩한다.
     *
     * @param storageUri S3 저장 URI
     * @return 원본 바이트 배열
     */
    suspend fun load(storageUri: String): ByteArray
}

/**
 * S3 저장소에서 문서 원본을 로딩한다.
 *
 * @property s3Client S3 클라이언트
 * @property s3Service S3 유틸 서비스
 * @property ingestionProperties 인제션 설정
 */
@Service
class S3DocumentContentLoader(
    private val s3Client: S3AsyncClient,
    private val s3Service: S3Service,
    private val ingestionProperties: DochibotIngestionProperties,
) : DocumentContentLoader {
    override suspend fun load(storageUri: String): ByteArray {
        val parsed = s3Service.parseStorageUri(storageUri)

        val head = s3Client.headObject(
            HeadObjectRequest.builder()
                .bucket(parsed.bucket)
                .key(parsed.key)
                .build()
        ).await()
        val maxBytes = ingestionProperties.content.maxBytes
        val contentLength = head.contentLength()
        if (contentLength != null && contentLength > maxBytes) {
            throw NonRetryableIngestionException(
                "문서 크기가 제한을 초과했습니다: size=${contentLength}B, max=${maxBytes}B"
            )
        }

        val request = GetObjectRequest.builder()
            .bucket(parsed.bucket)
            .key(parsed.key)
            .build()

        val responseBytes = s3Client.getObject(request, AsyncResponseTransformer.toBytes()).await()
        val bytes = responseBytes.asByteArray()
        if (bytes.size.toLong() > maxBytes) {
            throw NonRetryableIngestionException(
                "문서 크기가 제한을 초과했습니다: size=${bytes.size}B, max=${maxBytes}B"
            )
        }
        return bytes
    }
}
