package com.dochibot.feature.document.application

import com.dochibot.common.exception.CommonErrorCode
import com.dochibot.common.exception.DochiException
import com.dochibot.domain.repository.DocumentRepository
import com.dochibot.feature.document.dto.DocumentResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetDocumentUseCase(
    private val documentRepository: DocumentRepository,
) {
    /**
     * 문서 단건을 조회한다.
     *
     * @param documentId 문서 ID
     * @return 문서 정보
     */
    suspend fun execute(documentId: UUID): DocumentResponse {
        val document = documentRepository.findById(documentId)
            ?: throw DochiException(CommonErrorCode.NOT_FOUND, "Document not found: documentId=$documentId")

        return DocumentResponse.from(document)
    }
}
