package com.dochibot.feature.document.application

import com.dochibot.domain.enums.DocumentSourceType

/**
 * 문서 업로드 허용 포맷 정책.
 */
internal object DocumentUploadPolicy {
    const val UNSUPPORTED_FORMAT_MESSAGE =
        "Unsupported file format. Only PDF(.pdf) and Markdown(.md, .markdown) are allowed."

    private const val PDF_EXTENSION = "pdf"
    private val MARKDOWN_EXTENSIONS = setOf("md", "markdown")

    private const val PDF_MIME_TYPE = "application/pdf"
    private val MARKDOWN_MIME_TYPES = setOf("text/markdown", "text/x-markdown", "text/plain")

    fun isSupportedUpload(originalFilename: String, contentType: String): Boolean {
        val extension = extractExtension(originalFilename) ?: return false
        val normalizedContentType = normalizeContentType(contentType)

        return when {
            extension == PDF_EXTENSION -> normalizedContentType == PDF_MIME_TYPE
            extension in MARKDOWN_EXTENSIONS -> normalizedContentType in MARKDOWN_MIME_TYPES
            else -> false
        }
    }

    fun detectSourceType(originalFilename: String): DocumentSourceType? {
        val extension = extractExtension(originalFilename) ?: return null
        return when {
            extension == PDF_EXTENSION -> DocumentSourceType.PDF
            extension in MARKDOWN_EXTENSIONS -> DocumentSourceType.TEXT
            else -> null
        }
    }

    private fun normalizeContentType(contentType: String): String {
        return contentType.substringBefore(';').trim().lowercase()
    }

    private fun extractExtension(filename: String): String? {
        return filename.substringAfterLast('.', "").trim().lowercase().takeIf { it.isNotBlank() }
    }
}
