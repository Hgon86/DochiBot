package com.dochibot.feature.document.dto

import com.dochibot.domain.enums.DocumentStatus
import java.util.UUID

data class FinalizeDocumentUploadResponse(
    val documentId: UUID,
    val status: DocumentStatus,
    val ingestionJobId: UUID,
)
