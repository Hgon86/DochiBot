package com.dochibot.feature.document.dto

import com.dochibot.domain.enums.IngestionJobStatus
import java.util.UUID

data class ReindexDocumentResponse(
    val jobId: UUID,
    val status: IngestionJobStatus,
)
