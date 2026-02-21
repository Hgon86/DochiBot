package com.dochibot.feature.document.dto

data class CreateDocumentDownloadUrlResponse(
    val downloadUrl: String,
    val expiresInSeconds: Long,
)
