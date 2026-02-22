package com.dochibot.feature.document.application

import com.dochibot.domain.enums.DocumentSourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DocumentUploadPolicyTest {
    @Test
    fun `pdf와 markdown 업로드를 허용한다`() {
        assertTrue(DocumentUploadPolicy.isSupportedUpload("manual.pdf", "application/pdf"))
        assertTrue(DocumentUploadPolicy.isSupportedUpload("guide.md", "text/markdown"))
        assertTrue(DocumentUploadPolicy.isSupportedUpload("guide.markdown", "text/x-markdown"))
        assertTrue(DocumentUploadPolicy.isSupportedUpload("guide.md", "text/plain"))
    }

    @Test
    fun `지원하지 않는 확장자를 거부한다`() {
        assertFalse(DocumentUploadPolicy.isSupportedUpload("manual.txt", "text/plain"))
        assertFalse(DocumentUploadPolicy.isSupportedUpload("manual.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
    }

    @Test
    fun `확장자와 mime이 불일치하면 거부한다`() {
        assertFalse(DocumentUploadPolicy.isSupportedUpload("manual.pdf", "text/markdown"))
        assertFalse(DocumentUploadPolicy.isSupportedUpload("guide.md", "application/pdf"))
    }

    @Test
    fun `대소문자와 charset 파라미터를 정규화한다`() {
        assertTrue(DocumentUploadPolicy.isSupportedUpload("MANUAL.PDF", "Application/Pdf; charset=UTF-8"))
        assertTrue(DocumentUploadPolicy.isSupportedUpload("GUIDE.MD", "Text/Markdown; charset=utf-8"))
    }

    @Test
    fun `파일명에서 source type을 추론한다`() {
        assertEquals(DocumentSourceType.PDF, DocumentUploadPolicy.detectSourceType("manual.pdf"))
        assertEquals(DocumentSourceType.TEXT, DocumentUploadPolicy.detectSourceType("guide.markdown"))
        assertEquals(null, DocumentUploadPolicy.detectSourceType("manual.txt"))
    }
}
