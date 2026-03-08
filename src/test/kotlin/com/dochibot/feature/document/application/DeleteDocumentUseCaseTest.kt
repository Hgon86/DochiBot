package com.dochibot.feature.document.application

import com.dochibot.common.exception.CommonErrorCode
import com.dochibot.common.exception.DochiException
import com.dochibot.common.storage.service.S3Service
import com.dochibot.common.storage.util.ParsedS3Uri
import com.dochibot.domain.entity.Document
import com.dochibot.domain.enums.DocumentSourceType
import com.dochibot.domain.enums.DocumentStatus
import com.dochibot.domain.repository.DocumentRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.util.UUID
import kotlin.test.assertFailsWith

class DeleteDocumentUseCaseTest {
    private val documentRepository = mock(DocumentRepository::class.java)
    private val s3Service = mock(S3Service::class.java)
    private val useCase = DeleteDocumentUseCase(documentRepository, s3Service)

    @Test
    fun `storage uri가 있는 문서를 삭제한다`() = runTest {
        val documentId = UUID.randomUUID()
        val storageUri = "s3://dochi-bot/2026/03/manual.pdf"
        val document = createDocument(documentId = documentId, storageUri = storageUri)

        `when`(documentRepository.findById(documentId)).thenReturn(document)
        `when`(s3Service.parseStorageUri(storageUri)).thenReturn(
            ParsedS3Uri(bucket = "dochi-bot", key = "2026/03/manual.pdf"),
        )

        useCase.execute(documentId)

        verify(s3Service).deleteObject(bucket = "dochi-bot", key = "2026/03/manual.pdf")
        verify(documentRepository).delete(document)
    }

    @Test
    fun `processing 상태 문서는 삭제를 거부한다`() = runTest {
        val documentId = UUID.randomUUID()
        val document = createDocument(documentId = documentId, status = DocumentStatus.PROCESSING)

        `when`(documentRepository.findById(documentId)).thenReturn(document)

        val exception = assertFailsWith<DochiException> { useCase.execute(documentId) }

        assertEquals(CommonErrorCode.CONFLICT, exception.errorCode)
        verify(documentRepository).findById(documentId)
        verifyNoInteractions(s3Service)
    }

    @Test
    fun `없는 문서는 not found를 반환한다`() = runTest {
        val documentId = UUID.randomUUID()

        `when`(documentRepository.findById(documentId)).thenReturn(null)

        val exception = assertFailsWith<DochiException> { useCase.execute(documentId) }

        assertEquals(CommonErrorCode.NOT_FOUND, exception.errorCode)
        verify(documentRepository).findById(documentId)
        verifyNoInteractions(s3Service)
    }

    private fun createDocument(
        documentId: UUID,
        storageUri: String? = null,
        status: DocumentStatus = DocumentStatus.COMPLETED,
    ): Document {
        return Document.new(
            id = documentId,
            title = "테스트 문서",
            sourceType = DocumentSourceType.PDF,
            originalFilename = "manual.pdf",
            storageUri = storageUri,
            status = status,
        )
    }
}
