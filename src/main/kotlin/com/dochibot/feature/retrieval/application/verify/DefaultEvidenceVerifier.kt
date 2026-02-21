package com.dochibot.feature.retrieval.application.verify

import com.dochibot.common.config.DochibotRagProperties
import com.dochibot.feature.retrieval.dto.ChunkCandidate
import org.springframework.stereotype.Component
import kotlin.math.max

/**
 * Phase 2 기본 근거 검증기.
 *
 * - (옵션) top1 finalScore cutoff
 * - (옵션) query 토큰이 상위 N개 청크에서 얼마나 커버되는지(coverage)
 */
@Component
class DefaultEvidenceVerifier(
    private val ragProperties: DochibotRagProperties,
    private val queryTypeClassifier: QueryTypeClassifier,
) : EvidenceVerifier {
    /**
     * @param queryText 사용자 질의 원문
     * @param chunks 최종 컨텍스트 후보(topN)
     * @return 검증 결과
     */
    override fun verify(queryText: String, chunks: List<ChunkCandidate>): EvidenceVerification {
        if (chunks.isEmpty()) {
            return EvidenceVerification(
                isSufficient = false,
                reason = "NO_CHUNKS",
                tokenCoverage = 0.0,
            )
        }

        val cfg = ragProperties.verify
        val queryType = queryTypeClassifier.classify(queryText)

        val orderedChunks = chunks.sortedByDescending { it.finalScore }
        val top1 = orderedChunks.first()
        val top2 = orderedChunks.getOrNull(1)
        val gap = top2?.let { top1.finalScore - it.finalScore }
        val scope = orderedChunks.take(cfg.maxChunksToCheck)
        val sameDocSupportCount = scope
            .count { it.documentId == top1.documentId }
        val distinctDocCount = scope
            .map { it.documentId }
            .distinct()
            .size
        val queryTokens = tokenize(queryText)
        val adjustedMinTokenCoverage = adjustedMinTokenCoverage(queryType = queryType, base = cfg.minTokenCoverage)
        val coverage = tokenCoverage(
            queryTokens = queryTokens,
            chunks = scope,
        )

        if (cfg.maxDistinctDocs > 0 && distinctDocCount > cfg.maxDistinctDocs) {
            return EvidenceVerification(
                isSufficient = false,
                reason = formatReason(
                    code = "TOO_MANY_DISTINCT_DOCS",
                    details = mapOf(
                        "maxDistinctDocs" to cfg.maxDistinctDocs,
                        "distinctDocCount" to distinctDocCount,
                    )
                ),
                tokenCoverage = coverage,
                top1Score = top1.finalScore,
                top1Top2Gap = gap,
                sameDocSupportCount = sameDocSupportCount,
                distinctDocCount = distinctDocCount,
                queryType = queryType,
                appliedMinTokenCoverage = adjustedMinTokenCoverage,
            )
        }

        if (cfg.consistencyCheckEnabled) {
            val conflict = detectVersionConflict(scope)
            if (conflict != null) {
                return EvidenceVerification(
                    isSufficient = false,
                    reason = formatReason(
                        code = "CONFLICTING_FACTS",
                        details = mapOf(
                            "type" to conflict.type,
                            "values" to conflict.values.joinToString("|"),
                        )
                    ),
                    tokenCoverage = coverage,
                    top1Score = top1.finalScore,
                    top1Top2Gap = gap,
                    sameDocSupportCount = sameDocSupportCount,
                    distinctDocCount = distinctDocCount,
                    queryType = queryType,
                    appliedMinTokenCoverage = adjustedMinTokenCoverage,
                )
            }
        }

        if (cfg.minSameDocSupport > 1 && sameDocSupportCount < cfg.minSameDocSupport) {
            return EvidenceVerification(
                isSufficient = false,
                reason = formatReason(
                    code = "SAME_DOC_SUPPORT_BELOW_THRESHOLD",
                    details = mapOf(
                        "minSameDocSupport" to cfg.minSameDocSupport,
                        "sameDocSupportCount" to sameDocSupportCount,
                    )
                ),
                tokenCoverage = coverage,
                top1Score = top1.finalScore,
                top1Top2Gap = gap,
                sameDocSupportCount = sameDocSupportCount,
                distinctDocCount = distinctDocCount,
                queryType = queryType,
                appliedMinTokenCoverage = adjustedMinTokenCoverage,
            )
        }

        if (cfg.minTop1Top2Gap > 0.0 && gap != null && gap < cfg.minTop1Top2Gap) {
            return EvidenceVerification(
                isSufficient = false,
                reason = formatReason(
                    code = "TOP1_TOP2_GAP_BELOW_THRESHOLD",
                    details = mapOf(
                        "minTop1Top2Gap" to cfg.minTop1Top2Gap,
                        "actualGap" to gap,
                    )
                ),
                tokenCoverage = coverage,
                top1Score = top1.finalScore,
                top1Top2Gap = gap,
                sameDocSupportCount = sameDocSupportCount,
                distinctDocCount = distinctDocCount,
                queryType = queryType,
                appliedMinTokenCoverage = adjustedMinTokenCoverage,
            )
        }
        if (cfg.minTop1FinalScore > 0.0 && top1.finalScore < cfg.minTop1FinalScore) {
            return EvidenceVerification(
                isSufficient = false,
                reason = formatReason(
                    code = "TOP1_SCORE_BELOW_THRESHOLD",
                    details = mapOf(
                        "minTop1FinalScore" to cfg.minTop1FinalScore,
                        "actualTop1Score" to top1.finalScore,
                    )
                ),
                tokenCoverage = coverage,
                top1Score = top1.finalScore,
                top1Top2Gap = gap,
                sameDocSupportCount = sameDocSupportCount,
                distinctDocCount = distinctDocCount,
                queryType = queryType,
                appliedMinTokenCoverage = adjustedMinTokenCoverage,
            )
        }

        if (adjustedMinTokenCoverage > 0.0) {
            if (coverage < adjustedMinTokenCoverage) {
                return EvidenceVerification(
                    isSufficient = false,
                    reason = formatReason(
                        code = "TOKEN_COVERAGE_BELOW_THRESHOLD",
                        details = mapOf(
                            "queryType" to queryType,
                            "minTokenCoverage" to adjustedMinTokenCoverage,
                            "actualCoverage" to coverage,
                        )
                    ),
                    tokenCoverage = coverage,
                    top1Score = top1.finalScore,
                    top1Top2Gap = gap,
                    sameDocSupportCount = sameDocSupportCount,
                    distinctDocCount = distinctDocCount,
                    queryType = queryType,
                    appliedMinTokenCoverage = adjustedMinTokenCoverage,
                )
            }
            return EvidenceVerification(
                isSufficient = true,
                reason = "OK",
                tokenCoverage = coverage,
                top1Score = top1.finalScore,
                top1Top2Gap = gap,
                sameDocSupportCount = sameDocSupportCount,
                distinctDocCount = distinctDocCount,
                queryType = queryType,
                appliedMinTokenCoverage = adjustedMinTokenCoverage,
            )
        }

        return EvidenceVerification(
            isSufficient = true,
            reason = "OK",
            tokenCoverage = 0.0,
            top1Score = top1.finalScore,
            top1Top2Gap = gap,
            sameDocSupportCount = sameDocSupportCount,
            distinctDocCount = distinctDocCount,
            queryType = queryType,
            appliedMinTokenCoverage = adjustedMinTokenCoverage,
        )
    }

    /**
     * 질의 유형별로 토큰 커버리지 임계치를 보정한다.
     */
    private fun adjustedMinTokenCoverage(queryType: QueryType, base: Double): Double {
        return when (queryType) {
            QueryType.WHAT_WHO -> (base + 0.1).coerceIn(0.0, 1.0)
            QueryType.HOW_WHY -> (base - 0.1).coerceIn(0.0, 1.0)
            QueryType.OTHER -> base.coerceIn(0.0, 1.0)
        }
    }

    /**
     * 검증 실패 사유를 코드와 상세 key=value 포맷으로 생성한다.
     */
    private fun formatReason(code: String, details: Map<String, Any?>): String {
        if (details.isEmpty()) return code
        val tail = details.entries.joinToString(",") { "${it.key}=${it.value}" }
        return "$code|$tail"
    }

    /**
     * 상위 청크에서 버전 표기 충돌을 감지한다.
     */
    private fun detectVersionConflict(chunks: List<ChunkCandidate>): FactConflict? {
        if (chunks.isEmpty()) return null

        val versionRegex = Regex("(?i)(?:버전|version)\\s*([0-9]+(?:\\.[0-9]+)*)")
        val versions = chunks
            .asSequence()
            .flatMap { chunk ->
                versionRegex.findAll(chunk.text).map { it.groupValues[1] }
            }
            .toSet()

        return if (versions.size >= 2) {
            FactConflict(type = "VERSION", values = versions.sorted())
        } else {
            null
        }
    }

    private data class FactConflict(
        val type: String,
        val values: List<String>,
    )

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

    private fun tokenCoverage(queryTokens: List<String>, chunks: List<ChunkCandidate>): Double {
        if (queryTokens.isEmpty()) return 0.0
        if (chunks.isEmpty()) return 0.0

        val textTokens = chunks
            .asSequence()
            .flatMap { tokenize(it.text).asSequence() }
            .toSet()

        if (textTokens.isEmpty()) return 0.0

        val hits = queryTokens.count { it in textTokens }
        return hits.toDouble() / max(1, queryTokens.size).toDouble()
    }
}
