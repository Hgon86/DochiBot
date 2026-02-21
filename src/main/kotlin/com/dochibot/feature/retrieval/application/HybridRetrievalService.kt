package com.dochibot.feature.retrieval.application

import com.dochibot.common.config.DochibotRagProperties
import com.dochibot.feature.retrieval.infrastructure.metrics.RetrievalMetrics
import com.dochibot.feature.retrieval.infrastructure.log.RetrievalStructuredLogger
import com.dochibot.feature.retrieval.application.rerank.RerankInput
import com.dochibot.feature.retrieval.application.rerank.RerankedChunk
import com.dochibot.feature.retrieval.application.rerank.RerankerRouter
import com.dochibot.feature.retrieval.dto.ChunkCandidate
import com.dochibot.feature.retrieval.dto.SectionCandidate
import com.dochibot.feature.retrieval.repository.ChunkRetrievalRepository
import com.dochibot.feature.retrieval.repository.SectionRetrievalRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Gate -> Range-limited -> RRF -> (옵션) Rerank를 수행한다.
 *
 * @param sectionRetrievalRepository 섹션 검색 리포지토리
 * @param chunkRetrievalRepository 청크 검색 리포지토리
 * @param ragProperties RAG 설정
 * @param rerankerRouter 리랭커 라우터
 * @param retrievalMetrics 리트리벌 메트릭 기록기
 * @param retrievalStructuredLogger 리트리벌 구조화 로그 기록기
 */
@Service
class HybridRetrievalService(
    private val sectionRetrievalRepository: SectionRetrievalRepository,
    private val chunkRetrievalRepository: ChunkRetrievalRepository,
    private val ragProperties: DochibotRagProperties,
    private val rerankerRouter: RerankerRouter,
    private val retrievalMetrics: RetrievalMetrics,
    private val retrievalStructuredLogger: RetrievalStructuredLogger,
) {
    /**
     * 하이브리드 검색을 수행하여 상위 청크를 반환한다.
     *
     * @param queryText 질의 텍스트
     * @param queryEmbedding 질의 임베딩
     * @param finalTopK 최종 컨텍스트로 사용할 청크 수
     * @return 최종 순위의 청크 후보 리스트
     */
    suspend fun retrieveTopChunks(
        queryText: String,
        queryEmbedding: FloatArray,
        finalTopK: Int,
    ): List<ChunkCandidate> {
        require(finalTopK >= 1) { "finalTopK must be >= 1" }

        val startedAtNs = System.nanoTime()

        val gateDenseK = ragProperties.gate.denseTopK
        val gateSparseK = ragProperties.gate.sparseTopK
        val gateTopMSections = ragProperties.gate.sectionsTopM
        val chunkDenseK = ragProperties.retrieval.denseTopK
        val chunkSparseK = ragProperties.retrieval.sparseTopK

        val sectionDense = sectionRetrievalRepository.findDenseCandidates(queryEmbedding, gateDenseK)
        val sectionSparse = sectionRetrievalRepository.findSparseCandidates(queryText, gateSparseK)
        val gatedSections = fuseSections(sectionDense, sectionSparse)
            .take(gateTopMSections)
        val sectionIds = gatedSections.map { it.id }

        retrievalMetrics.recordCandidateCount(stage = "section_dense", count = sectionDense.size)
        retrievalMetrics.recordCandidateCount(stage = "section_sparse", count = sectionSparse.size)
        retrievalMetrics.recordCandidateCount(stage = "section_gated", count = gatedSections.size)

        val chunkDense = if (sectionIds.isEmpty()) {
            chunkRetrievalRepository.findDenseCandidates(queryEmbedding, chunkDenseK)
        } else {
            chunkRetrievalRepository.findDenseCandidatesBySectionIds(sectionIds, queryEmbedding, chunkDenseK)
        }
        val chunkSparse = if (sectionIds.isEmpty()) {
            chunkRetrievalRepository.findSparseCandidates(queryText, chunkSparseK)
        } else {
            chunkRetrievalRepository.findSparseCandidatesBySectionIds(sectionIds, queryText, chunkSparseK)
        }
        val fused = fuseChunks(chunkDense, chunkSparse)
        retrievalMetrics.recordCandidateCount(stage = "chunk_dense", count = chunkDense.size)
        retrievalMetrics.recordCandidateCount(stage = "chunk_sparse", count = chunkSparse.size)
        retrievalMetrics.recordCandidateCount(stage = "chunk_fused", count = fused.size)

        val result = rerankIfEnabled(queryText, fused, finalTopK)
        retrievalMetrics.recordCandidateCount(stage = "returned", count = result.size)
        result.firstOrNull()?.let { retrievalMetrics.recordTop1Score(it.finalScore) }

        val tookMs = (System.nanoTime() - startedAtNs) / 1_000_000
        retrievalMetrics.recordRetrievalLatency(tookMs)

        retrievalStructuredLogger.logRetrievalResult(
            queryText = queryText,
            fused = fused,
            result = result,
            top1FinalScore = result.firstOrNull()?.finalScore,
            tookMs = tookMs,
        )

        return result
    }

    /**
     * 리랭크가 활성화된 경우 리랭크를 수행하고, 그렇지 않으면 RRF 점수를 그대로 사용한다.
     *
     * @param queryText 질의 텍스트
     * @param candidates RRF 결합 결과 (finalScore = rrfScore)
     * @param finalTopK 최종 컨텍스트 수
     * @return 최종 컨텍스트 청크 (rerankScore가 있으면 finalScore = rerankScore)
     */
    private suspend fun rerankIfEnabled(
        queryText: String,
        candidates: List<ChunkCandidate>,
        finalTopK: Int,
    ): List<ChunkCandidate> {
        if (!ragProperties.rerank.enabled) {
            return candidates.take(finalTopK)
        }

        val topK = ragProperties.rerank.candidatesTopK
        val input = RerankInput(
            query = queryText,
            candidates = candidates.take(topK),
        )

        val rerankStartedAt = System.nanoTime()
        val reranked = rerankerRouter.rerank(ragProperties.rerank.model, input)
        val rerankTookMs = (System.nanoTime() - rerankStartedAt) / 1_000_000
        retrievalMetrics.recordRerankLatency(
            latencyMs = rerankTookMs,
            model = ragProperties.rerank.model,
        )
        return applyRerankScores(reranked).take(finalTopK)
    }

    /**
     * 리랭크 결과에 점수를 적용하여 ChunkCandidate로 변환한다.
     *
     * @param reranked 리랭크 결과
     * @return rerankScore와 finalScore가 설정된 후보 리스트
     */
    private fun applyRerankScores(reranked: List<RerankedChunk>): List<ChunkCandidate> {
        return reranked.map { it.candidate.withRerankScore(it.score) }
    }

    /**
     * dense와 sparse 섹션 후보를 RRF로 융합한다.
     *
     * @param dense dense 검색 결과
     * @param sparse sparse 검색 결과
     * @return RRF 점수 기준 정렬된 섹션 리스트
     */
    private fun fuseSections(
        dense: List<SectionCandidate>,
        sparse: List<SectionCandidate>,
    ): List<SectionCandidate> {
        return fuseCandidatesByRrf(
            dense = dense,
            sparse = sparse,
            idSelector = { it.id },
            scoreSelector = { it.rrfScore },
            distanceSelector = { it.distance },
        ) { candidate, existing, denseRank, sparseRank, rrfScore ->
            SectionCandidate(
                id = candidate.id,
                documentId = candidate.documentId,
                documentTitle = candidate.documentTitle,
                heading = candidate.heading.ifBlank { existing?.heading.orEmpty() },
                sectionPath = candidate.sectionPath.ifBlank { existing?.sectionPath.orEmpty() },
                distance = candidate.distance ?: existing?.distance,
                sparseScore = candidate.sparseScore ?: existing?.sparseScore,
                denseRank = denseRank,
                sparseRank = sparseRank,
                rrfScore = rrfScore,
            )
        }
    }

    /**
     * dense와 sparse 청크 후보를 RRF로 융합한다.
     * finalScore는 rrfScore로 초기화된다.
     *
     * @param dense dense 검색 결과
     * @param sparse sparse 검색 결과
     * @return finalScore(=rrfScore) 기준 정렬된 청크 리스트
     */
    private fun fuseChunks(
        dense: List<ChunkCandidate>,
        sparse: List<ChunkCandidate>,
    ): List<ChunkCandidate> {
        return fuseCandidatesByRrf(
            dense = dense,
            sparse = sparse,
            idSelector = { it.id },
            scoreSelector = { it.finalScore },
            distanceSelector = { it.distance },
        ) { candidate, existing, denseRank, sparseRank, rrfScore ->
            ChunkCandidate(
                id = candidate.id,
                documentId = candidate.documentId,
                documentTitle = candidate.documentTitle,
                sectionId = candidate.sectionId,
                sectionPath = candidate.sectionPath ?: existing?.sectionPath,
                text = candidate.text.ifBlank { existing?.text.orEmpty() },
                page = candidate.page ?: existing?.page,
                distance = candidate.distance ?: existing?.distance,
                sparseScore = candidate.sparseScore ?: existing?.sparseScore,
                denseRank = denseRank,
                sparseRank = sparseRank,
                rrfScore = rrfScore,
                finalScore = rrfScore,
            )
        }
    }

    /**
     * dense/sparse 후보를 RRF로 공통 융합한다.
     *
     * @param dense dense 검색 결과
     * @param sparse sparse 검색 결과
     * @param idSelector 후보 ID 추출 함수
     * @param scoreSelector 최종 정렬 점수 추출 함수
     * @param distanceSelector 동점 시 거리 추출 함수
     * @param merger 기존/신규 후보 병합 함수
     * @return 점수 내림차순, 거리 오름차순 정렬 후보
     */
    private fun <T> fuseCandidatesByRrf(
        dense: List<T>,
        sparse: List<T>,
        idSelector: (T) -> UUID,
        scoreSelector: (T) -> Double,
        distanceSelector: (T) -> Double?,
        merger: (candidate: T, existing: T?, denseRank: Int?, sparseRank: Int?, rrfScore: Double) -> T,
    ): List<T> {
        val rrfK = ragProperties.fusion.rrfK
        val denseRanks = dense.withIndex().associate { idSelector(it.value) to (it.index + 1) }
        val sparseRanks = sparse.withIndex().associate { idSelector(it.value) to (it.index + 1) }
        val byId = LinkedHashMap<UUID, T>()

        fun upsert(candidate: T) {
            val id = idSelector(candidate)
            val existing = byId[id]
            val denseRank = denseRanks[id]
            val sparseRank = sparseRanks[id]
            val rrfScore = calculateRrfScore(denseRank, sparseRank, rrfK)

            byId[id] = merger(candidate, existing, denseRank, sparseRank, rrfScore)
        }

        dense.forEach(::upsert)
        sparse.forEach(::upsert)

        return byId.values
            .sortedWith(compareByDescending<T> { scoreSelector(it) }.thenBy { distanceSelector(it) ?: Double.MAX_VALUE })
    }

    /**
     * RRF 점수를 계산한다.
     *
     * @param denseRank dense 결과 순위 (없으면 null)
     * @param sparseRank sparse 결과 순위 (없으면 null)
     * @param rrfK RRF 상수
     * @return dense와 sparse RRF 점수의 합
     */
    private fun calculateRrfScore(denseRank: Int?, sparseRank: Int?, rrfK: Int): Double {
        val denseScore = denseRank?.let { RrfFusion.score(it, rrfK) } ?: 0.0
        val sparseScore = sparseRank?.let { RrfFusion.score(it, rrfK) } ?: 0.0
        return denseScore + sparseScore
    }
}
