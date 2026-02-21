package com.dochibot.feature.document.dto

import com.dochibot.domain.entity.Document
import com.dochibot.domain.enums.DocumentSourceType
import com.dochibot.domain.enums.DocumentStatus
import java.time.Instant
import java.util.UUID

data class DocumentResponse(
    val id: UUID,
    val title: String,
    val sourceType: DocumentSourceType,
    val originalFilename: String? = null,
    val storageUri: String? = null,
    val status: DocumentStatus,
    val errorMessage: String? = null,
    val createdByUserId: UUID? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    companion object {
        fun from(document: Document): DocumentResponse {
            return DocumentResponse(
                id = document.id,
                title = document.title,
                sourceType = document.sourceType,
                originalFilename = document.originalFilename,
                storageUri = document.storageUri,
                status = document.status,
                errorMessage = document.errorMessage,
                createdByUserId = document.createdByUserId,
                createdAt = document.createdAt,
                updatedAt = document.updatedAt,
            )
        }
    }
}
