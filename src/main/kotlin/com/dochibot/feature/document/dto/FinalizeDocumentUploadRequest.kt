package com.dochibot.feature.document.dto

import com.dochibot.domain.enums.DocumentSourceType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class FinalizeDocumentUploadRequest(
    @field:NotNull
    val documentId: UUID,
    @field:NotBlank
    val title: String,
    @field:NotNull
    val sourceType: DocumentSourceType,
    val originalFilename: String? = null,
    @field:NotBlank
    val storageUri: String,
)
