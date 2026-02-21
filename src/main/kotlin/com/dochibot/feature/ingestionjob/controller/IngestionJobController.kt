package com.dochibot.feature.ingestionjob.controller

import com.dochibot.feature.ingestionjob.application.ListIngestionJobsUseCase
import com.dochibot.feature.ingestionjob.dto.ListIngestionJobsResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/ingestion-jobs")
class IngestionJobController(
    private val listIngestionJobsUseCase: ListIngestionJobsUseCase,
) {
    /**
     * 인덱싱 작업 목록을 조회한다.
     *
     * @param documentId (옵션) 문서 ID 필터
     * @param limit (옵션) 조회 개수(기본 50, 최대 100)
     * @param offset (옵션) 오프셋(기본 0)
     * @return 인덱싱 작업 목록
     */
    @GetMapping
    suspend fun list(
        @RequestParam(required = false) documentId: UUID?,
        @RequestParam(required = false, defaultValue = "50") limit: Int,
        @RequestParam(required = false, defaultValue = "0") offset: Long,
    ): ListIngestionJobsResponse {
        val items = listIngestionJobsUseCase.execute(documentId = documentId, limit = limit, offset = offset)
        return ListIngestionJobsResponse(items = items)
    }
}
