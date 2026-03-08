package com.dochibot.feature.retrieval.application.rerank

import com.dochibot.feature.retrieval.dto.ChunkCandidate
import org.springframework.stereotype.Component
import kotlin.math.max

/**
 * 휴리스틱 기반 리랭커(Phase 2 베이스라인).
 *
 * - sparse 점수(FTS)와 query 토큰 오버랩을 결합해 재정렬한다.
 */
@Component
class HeuristicReranker : Reranker {
    /**
     * @param input 질의/후보 입력
     * @return 점수 내림차순 결과
     */
    override suspend fun rerank(input: RerankInput): List<RerankedChunk> {
        if (input.candidates.isEmpty()) return emptyList()

        val queryTokens = tokenize(input.query)
        val scored = input.candidates.map { candidate ->
            val tokenScore = tokenOverlapScore(queryTokens, candidate)
            val sparseScore = candidate.sparseScore ?: 0.0
            val combined = sparseScore * 0.6 + tokenScore * 0.4
            candidate to combined
        }

        return scored
            .sortedWith(compareByDescending<Pair<ChunkCandidate, Double>> { it.second }
                .thenByDescending { it.first.rrfScore })
            .mapIndexed { idx, (candidate, score) ->
                RerankedChunk(candidate = candidate, score = score, rank = idx + 1)
            }
    }

    private fun tokenize(text: String): List<String> {
        return text
            .lowercase()
            .split(Regex("[^a-z0-9가-힣]+"))
            .filter { shouldKeepToken(it) }
    }

    private fun shouldKeepToken(token: String): Boolean {
        if (token.isBlank()) return false
        val hasHangul = token.any { it in '가'..'힣' }
        return if (hasHangul) {
            token.length >= 1
        } else {
            token.length >= 2
        }
    }

    private fun tokenOverlapScore(queryTokens: List<String>, candidate: ChunkCandidate): Double {
        if (queryTokens.isEmpty()) return 0.0
        val searchableText = buildString(candidate.documentTitle.length + candidate.text.length + 64) {
            append(candidate.documentTitle)
            append(' ')
            if (!candidate.sectionPath.isNullOrBlank()) {
                append(candidate.sectionPath)
                append(' ')
            }
            append(candidate.text)
        }
        val textTokens = tokenize(searchableText).toSet()
        if (textTokens.isEmpty()) return 0.0
        val hits = queryTokens.count { it in textTokens }
        return hits.toDouble() / max(1, queryTokens.size).toDouble()
    }
}
