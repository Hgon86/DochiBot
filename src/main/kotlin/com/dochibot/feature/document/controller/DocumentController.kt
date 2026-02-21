package com.dochibot.feature.document.controller

import com.dochibot.domain.enums.DocumentStatus
import com.dochibot.feature.document.application.CreateDocumentDownloadUrlUseCase
import com.dochibot.feature.document.application.CreateDocumentUploadUrlUseCase
import com.dochibot.feature.document.application.FinalizeDocumentUploadUseCase
import com.dochibot.feature.document.application.GetDocumentUseCase
import com.dochibot.feature.document.application.ListDocumentsUseCase
import com.dochibot.feature.document.application.ReindexDocumentUseCase
import com.dochibot.feature.document.dto.CreateDocumentDownloadUrlResponse
import com.dochibot.feature.document.dto.CreateDocumentUploadUrlRequest
import com.dochibot.feature.document.dto.CreateDocumentUploadUrlResponse
import com.dochibot.feature.document.dto.DocumentResponse
import com.dochibot.feature.document.dto.FinalizeDocumentUploadRequest
import com.dochibot.feature.document.dto.FinalizeDocumentUploadResponse
import com.dochibot.feature.document.dto.ListDocumentsResponse
import com.dochibot.feature.document.dto.ReindexDocumentResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestParam
import java.util.UUID

@RestController
@RequestMapping("/api/v1/documents")
class DocumentController(
    private val createDocumentUploadUrlUseCase: CreateDocumentUploadUrlUseCase,
    private val finalizeDocumentUploadUseCase: FinalizeDocumentUploadUseCase,
    private val listDocumentsUseCase: ListDocumentsUseCase,
    private val getDocumentUseCase: GetDocumentUseCase,
    private val createDocumentDownloadUrlUseCase: CreateDocumentDownloadUrlUseCase,
    private val reindexDocumentUseCase: ReindexDocumentUseCase,
) {
    private val log = KotlinLogging.logger {}

    /**
     * 업로드용 presigned PUT URL을 발급한다.
     *
     * @param jwt 인증 주체(JWT)
     * @param request 업로드 URL 발급 요청
     * @return 업로드 URL 및 저장 위치 정보
     */
    @PostMapping("/upload-url")
    suspend fun createUploadUrl(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: CreateDocumentUploadUrlRequest,
    ): CreateDocumentUploadUrlResponse {
        log.info { "Create upload-url requested: userId=${jwt.subject}, filename=${request.originalFilename}" }
        return createDocumentUploadUrlUseCase.execute(request)
    }

    /**
     * 업로드된 오브젝트를 문서로 등록하고 인덱싱 작업을 생성한다.
     *
     * @param jwt 인증 주체(JWT)
     * @param request 업로드 확정 요청
     * @return 생성된 문서/작업 정보
     */
    @PostMapping
    suspend fun finalizeUpload(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: FinalizeDocumentUploadRequest,
    ): FinalizeDocumentUploadResponse {
        log.info { "Finalize upload requested: userId=${jwt.subject}, documentId=${request.documentId}" }
        return finalizeDocumentUploadUseCase.execute(jwt, request)
    }

    /**
     * 문서 목록을 조회한다.
     *
     * @param status (옵션) 문서 상태 필터
     * @param limit (옵션) 조회 개수(기본 50, 최대 100)
     * @param offset (옵션) 오프셋(기본 0)
     * @return 문서 목록
     */
    @GetMapping
    suspend fun listDocuments(
        @RequestParam(required = false) status: DocumentStatus?,
        @RequestParam(required = false, defaultValue = "50") limit: Int,
        @RequestParam(required = false, defaultValue = "0") offset: Long,
    ): ListDocumentsResponse {
        val items = listDocumentsUseCase.execute(status = status, limit = limit, offset = offset)
        return ListDocumentsResponse(items = items)
    }

    /**
     * 문서 단건을 조회한다.
     *
     * @param documentId 문서 ID
     * @return 문서 정보
     */
    @GetMapping("/{documentId}")
    suspend fun getDocument(
        @PathVariable documentId: UUID,
    ): DocumentResponse {
        return getDocumentUseCase.execute(documentId)
    }

    /**
     * 다운로드용 presigned GET URL을 발급한다.
     *
     * @param documentId 문서 ID
     * @param filename (옵션) 다운로드 파일명
     * @return 다운로드 URL
     */
    @GetMapping("/{documentId}/download-url")
    suspend fun createDownloadUrl(
        @PathVariable documentId: UUID,
        @RequestParam(required = false) filename: String?,
    ): CreateDocumentDownloadUrlResponse {
        return createDocumentDownloadUrlUseCase.execute(documentId, filename)
    }

    /**
     * 문서 재인덱싱 작업을 생성한다.
     *
     * @param documentId 문서 ID
     * @return 생성된 작업 정보
     */
    @PostMapping("/{documentId}/reindex")
    suspend fun reindex(
        @PathVariable documentId: UUID,
    ): ReindexDocumentResponse {
        return reindexDocumentUseCase.execute(documentId)
    }
}
