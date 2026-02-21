package com.dochibot.common.storage.util

import com.dochibot.common.exception.CommonErrorCode
import com.dochibot.common.exception.DochiException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * S3 호환 스토리지에서 사용하는 순수 문자열/URI 처리 유틸.
 */
object S3StorageUtils {
    private val clock: Clock = Clock.systemUTC()
    private val yearMonthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM")

    /**
     * 현재 UTC 시간을 반환한다.
     *
     * @return 현재 시각(UTC)
     */
    fun nowUtc(): Instant = clock.instant()

    /**
     * 문서 업로드 오브젝트 key 규칙을 생성한다.
     *
     * 형식: yyyy/MM/{documentId}_{filename}
     *
     * @param documentId 문서 ID
     * @param originalFilename 업로드 원본 파일명
     * @param now 기준 시각(UTC)
     * @return 생성된 오브젝트 key
     */
    fun buildDocumentObjectKey(
        documentId: UUID,
        originalFilename: String,
        now: Instant = nowUtc(),
    ): String {
        val prefix = yearMonthFormat.format(now.atZone(ZoneOffset.UTC))
        val safeFilename = sanitizeObjectFilename(originalFilename)
        return "$prefix/${documentId}_$safeFilename"
    }

    /**
     * S3 오브젝트 key에 사용할 파일명을 sanitize 한다.
     *
     * @param originalFilename 원본 파일명
     * @return sanitize 된 파일명
     */
    private fun sanitizeObjectFilename(originalFilename: String): String {
        val baseName = originalFilename
            .replace('\\', '/')
            .substringAfterLast('/')

        val cleaned = baseName
            .filter { ch -> !ch.isISOControl() }
            .trim()

        val safe = cleaned
            .replace(Regex("\\/+"), "_")
            .replace(Regex("\\s+"), "_")
            .replace('"', '_')
            .ifBlank { "file" }

        return safe.take(120)
    }

    /**
     * 다운로드 응답용 Content-Disposition(attachment)을 생성한다.
     *
     * @param filename 다운로드 파일명
     * @return Content-Disposition 값
     */
    fun buildAttachmentContentDisposition(filename: String): String {
        val cleaned = filename.trim().replace('"', '_').replace(';', '_')
        val asciiFallback = cleaned
            .map { ch -> if (ch.code in 0x20..0x7E) ch else '_' }
            .joinToString("")
            .ifBlank { "download" }
            .take(120)

        val encoded = URLEncoder.encode(cleaned, StandardCharsets.UTF_8)
            .replace("+", "%20")

        return "attachment; filename=\"$asciiFallback\"; filename*=UTF-8''$encoded"
    }

    /**
     * S3 저장 URI를 생성한다.
     *
     * 형식: s3://{bucket}/{key}
     */
    fun toStorageUri(bucket: String, key: String): String {
        val normalizedKey = key.trimStart('/')
        return "s3://$bucket/$normalizedKey"
    }

    /**
     * S3 저장 URI를 파싱한다.
     *
     * 형식: s3://{bucket}/{key}
     */
    fun parseStorageUri(storageUri: String): ParsedS3Uri {
        val uri = try {
            URI.create(storageUri)
        } catch (e: IllegalArgumentException) {
            throw DochiException(CommonErrorCode.BAD_REQUEST, "Invalid storageUri", e)
        }

        if (uri.scheme != "s3") {
            throw DochiException(CommonErrorCode.BAD_REQUEST, "Invalid storageUri scheme: ${uri.scheme}")
        }

        val bucket = uri.host?.takeIf { it.isNotBlank() }
            ?: throw DochiException(CommonErrorCode.BAD_REQUEST, "Invalid storageUri bucket")

        val key = uri.path?.removePrefix("/")?.takeIf { it.isNotBlank() }
            ?: throw DochiException(CommonErrorCode.BAD_REQUEST, "Invalid storageUri key")

        return ParsedS3Uri(bucket = bucket, key = key)
    }
}

/**
 * s3://{bucket}/{key} 파싱 결과.
 *
 * @property bucket 버킷
 * @property key 오브젝트 key
 */
data class ParsedS3Uri(
    val bucket: String,
    val key: String,
)
