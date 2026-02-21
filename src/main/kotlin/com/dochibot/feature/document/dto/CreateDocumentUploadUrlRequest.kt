package com.dochibot.feature.document.dto

import jakarta.validation.constraints.NotBlank

data class CreateDocumentUploadUrlRequest(
    @field:NotBlank
    val originalFilename: String,
    @field:NotBlank
    val contentType: String,
)
