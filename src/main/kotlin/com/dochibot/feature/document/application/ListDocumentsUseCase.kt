package com.dochibot.feature.document.application

import com.dochibot.domain.enums.DocumentStatus
import com.dochibot.domain.repository.DocumentRepository
import com.dochibot.feature.document.dto.DocumentResponse
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service

@Service
class ListDocumentsUseCase(
    private val documentRepository: DocumentRepository,
) {
    /**
     * 문서 목록을 조회한다.
     *
     * @param status (옵션) 문서 상태 필터
     * @param limit 조회 개수
     * @param offset 오프셋
     * @return 문서 목록
     */
    suspend fun execute(status: DocumentStatus?, limit: Int, offset: Long): List<DocumentResponse> {
        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)

        val documents = if (status == null) {
            documentRepository.findPage(limit = safeLimit, offset = safeOffset).toList()
        } else {
            documentRepository.findPageByStatus(status = status.name, limit = safeLimit, offset = safeOffset).toList()
        }

        return documents.map { DocumentResponse.from(it) }
    }
}
