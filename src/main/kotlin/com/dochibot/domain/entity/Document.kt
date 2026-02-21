package com.dochibot.domain.entity

import com.dochibot.common.util.id.Uuid7Generator
import com.dochibot.domain.enums.DocumentLanguage
import com.dochibot.domain.enums.DocumentSourceType
import com.dochibot.domain.enums.DocumentStatus
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * 업로드된 문서 메타데이터 Entity.
 *
 * @property id 문서 UUID (앱에서 UUIDv7로 생성)
 * @property title 문서 표시명
 * @property sourceType 소스 타입 (PDF/TEXT)
 * @property originalFilename 원본 파일명 (파일 업로드 시)
 * @property storageUri S3 저장 URI (형식: s3://dochi-bot/{year}/{month}/{uuid}_{filename})
 * @property status 처리 상태 (PENDING/PROCESSING/COMPLETED/FAILED)
 * @property errorMessage 처리 실패 시 에러 메시지
 * @property createdByUserId 업로드한 사용자 ID (nullable, 추후 감사로그용)
 * @property language 문서 언어(KO/EN/UNKNOWN)
 * @property createdAt 생성 시각 (Auditing 자동 채움)
 * @property updatedAt 수정 시각 (Auditing 자동 채움)
 */
@Table("documents")
data class Document(
    @get:JvmName("getDocumentId")
    @field:Id
    val id: UUID = Uuid7Generator.create(),
    val title: String,
    val sourceType: DocumentSourceType,
    val originalFilename: String? = null,
    val storageUri: String? = null,
    val status: DocumentStatus = DocumentStatus.PENDING,
    val errorMessage: String? = null,
    val createdByUserId: UUID? = null,
    val language: DocumentLanguage = DocumentLanguage.UNKNOWN,
    @CreatedDate
    val createdAt: Instant? = null,
    @LastModifiedDate
    val updatedAt: Instant? = null
) : BasePersistableUuidEntity() {
    override fun getId(): UUID = id

    companion object {
        fun new(
            id: UUID = Uuid7Generator.create(),
            title: String,
            sourceType: DocumentSourceType,
            originalFilename: String? = null,
            storageUri: String? = null,
            status: DocumentStatus = DocumentStatus.PENDING,
            errorMessage: String? = null,
            createdByUserId: UUID? = null,
            language: DocumentLanguage = DocumentLanguage.UNKNOWN,
        ): Document {
            val entity = Document(
                id = id,
                title = title,
                sourceType = sourceType,
                originalFilename = originalFilename,
                storageUri = storageUri,
                status = status,
                errorMessage = errorMessage,
                createdByUserId = createdByUserId,
                language = language,
            )

            entity.markAsNew()
            return entity
        }
    }
}
