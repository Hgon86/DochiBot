package com.dochibot.feature.ingestionjob.application

import com.dochibot.domain.repository.DocumentIngestionJobRepository
import com.dochibot.feature.ingestionjob.dto.IngestionJobResponse
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ListIngestionJobsUseCase(
    private val documentIngestionJobRepository: DocumentIngestionJobRepository,
) {
    /**
     * 인덱싱 작업 목록을 조회한다.
     *
     * @param documentId (옵션) 문서 ID 필터
     * @param limit 조회 개수
     * @param offset 오프셋
     * @return 작업 목록
     */
    suspend fun execute(documentId: UUID?, limit: Int, offset: Long): List<IngestionJobResponse> {
        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)

        val jobs = if (documentId == null) {
            documentIngestionJobRepository.findPage(limit = safeLimit, offset = safeOffset).toList()
        } else {
            documentIngestionJobRepository.findPageByDocumentId(
                documentId = documentId,
                limit = safeLimit,
                offset = safeOffset,
            ).toList()
        }

        return jobs.map { IngestionJobResponse.from(it) }
    }
}
