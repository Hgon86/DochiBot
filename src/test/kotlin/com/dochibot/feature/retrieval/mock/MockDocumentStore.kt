package com.dochibot.feature.retrieval.mock

import com.dochibot.feature.retrieval.dto.ChunkCandidate
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.math.max

/**
 * 평가셋 전용 Mock 문서 저장소.
 *
 * 실데이터/DB 없이도 평가 파이프라인을 점검할 수 있도록
 * 인메모리 문서/청크 컬렉션과 간단한 토큰 오버랩 검색을 제공한다.
 */
class MockDocumentStore private constructor(
    private val documents: List<MockDocument>,
) {
    private val allChunks: List<ChunkCandidate> = documents.flatMap { document ->
        document.chunks.map { chunk ->
            ChunkCandidate(
                id = chunk.id,
                documentId = document.id,
                documentTitle = document.title,
                sectionId = chunk.sectionId,
                sectionPath = chunk.sectionPath,
                text = chunk.text,
                page = chunk.page,
                finalScore = 0.0,
            )
        }
    }
    private val chunkById: Map<UUID, ChunkCandidate> = allChunks.associateBy { it.id }

    /**
     * 저장된 전체 청크를 반환한다.
     *
     * @return 전체 청크 목록
     */
    fun findAllChunks(): List<ChunkCandidate> = allChunks

    /**
     * ID로 청크를 조회한다.
     *
     * @param chunkId 조회할 청크 ID
     * @return 청크(없으면 null)
     */
    fun findChunkById(chunkId: UUID): ChunkCandidate? = chunkById[chunkId]

    /**
     * 문서 ID로 청크 목록을 조회한다.
     *
     * @param documentId 조회할 문서 ID
     * @return 문서에 속한 청크 목록
     */
    fun findChunksByDocumentId(documentId: UUID): List<ChunkCandidate> =
        allChunks.filter { it.documentId == documentId }

    /**
     * 질의 토큰 오버랩 기반으로 상위 청크를 검색한다.
     *
     * @param query 질의 텍스트
     * @param topK 반환할 상위 개수
     * @return finalScore 기준 내림차순 정렬 결과
     */
    fun retrieveTopChunks(query: String, topK: Int): List<ChunkCandidate> {
        require(topK >= 1) { "topK must be >= 1" }
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) {
            return allChunks.take(topK)
        }

        return allChunks
            .map { chunk -> chunk.copy(finalScore = score(queryTokens, chunk)) }
            .sortedWith(
                compareByDescending<ChunkCandidate> { it.finalScore }
                    .thenBy { it.documentTitle }
                    .thenBy { it.id },
            )
            .take(topK)
    }

    /**
     * 질의-청크 매칭 점수를 계산한다.
     *
     * @param queryTokens 질의 토큰 집합
     * @param candidate 평가 대상 후보
     * @return 0 이상 매칭 점수
     */
    private fun score(queryTokens: Set<String>, candidate: ChunkCandidate): Double {
        val titleTokens = tokenize(candidate.documentTitle)
        val sectionTokens = tokenize(candidate.sectionPath.orEmpty())
        val textTokens = tokenize(candidate.text)
        val overlapOnTitle = queryTokens.intersect(titleTokens).size
        val overlapOnSection = queryTokens.intersect(sectionTokens).size
        val overlapOnText = queryTokens.intersect(textTokens).size

        val titleCoverage = overlapOnTitle.toDouble() / max(1, queryTokens.size)
        val sectionCoverage = overlapOnSection.toDouble() / max(1, queryTokens.size)
        val textCoverage = overlapOnText.toDouble() / max(1, queryTokens.size)

        return (titleCoverage * 1.2) + (sectionCoverage * 0.8) + (textCoverage * 1.0)
    }

    /**
     * 문자열을 검색용 토큰 집합으로 변환한다.
     *
     * @param raw 원본 문자열
     * @return 공백/기호 기준 분리된 소문자 토큰 집합
     */
    private fun tokenize(raw: String): Set<String> = raw
        .lowercase()
        .split(TOKEN_SPLIT_REGEX)
        .asSequence()
        .map { it.trim() }
        .filter { it.length >= 2 }
        .toSet()

    companion object {
        private val TOKEN_SPLIT_REGEX = Regex("[^a-zA-Z0-9가-힣]+")

        /**
         * JSON 문자열로 MockDocumentStore를 생성한다.
         *
         * @param json mock 데이터셋 JSON
         * @param objectMapper Jackson ObjectMapper
         * @return 생성된 저장소
         */
        fun fromJson(json: String, objectMapper: ObjectMapper): MockDocumentStore {
            val data = objectMapper.readValue(json, MockCorpus::class.java)
            return MockDocumentStore(data.documents)
        }
    }
}

/**
 * Mock 문서 코퍼스 루트 모델.
 *
 * @property documents 문서 목록
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MockCorpus(
    val documents: List<MockDocument> = emptyList(),
)

/**
 * Mock 문서 모델.
 *
 * @property id 문서 ID
 * @property title 문서 제목
 * @property chunks 문서 청크 목록
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MockDocument(
    val id: UUID,
    val title: String,
    val chunks: List<MockChunk> = emptyList(),
)

/**
 * Mock 청크 모델.
 *
 * @property id 청크 ID
 * @property sectionId 섹션 ID
 * @property sectionPath 섹션 경로
 * @property page 페이지 번호(옵션)
 * @property text 청크 본문
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MockChunk(
    val id: UUID,
    val sectionId: UUID,
    val sectionPath: String? = null,
    val page: Int? = null,
    val text: String,
)
