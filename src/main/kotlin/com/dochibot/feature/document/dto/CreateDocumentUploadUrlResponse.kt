package com.dochibot.feature.document.dto

import java.util.UUID

data class CreateDocumentUploadUrlResponse(
    val documentId: UUID,
    val bucket: String,
    val key: String,
    val storageUri: String,
    val uploadUrl: String,
    val method: String,
    val expiresInSeconds: Long,
    val requiredHeaders: Map<String, String> = emptyMap(),
)
