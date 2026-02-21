package com.dochibot.feature.retrieval.application.verify

import org.springframework.stereotype.Component

/**
 * 질의 문장을 단순 규칙 기반으로 유형 분류한다.
 */
@Component
class QueryTypeClassifier {
    private val whatWhoKeywords = setOf(
        "what", "who", "which", "정의", "뜻", "무엇", "뭐", "누구", "어떤",
    )
    private val howWhyKeywords = setOf(
        "how", "why", "method", "process", "어떻게", "왜", "방법", "절차", "원인",
    )
    private val koreanHowWhyHints = setOf("어떻게", "왜", "방법", "절차", "원인")
    private val koreanWhatWhoHints = setOf("정의", "뜻", "무엇", "뭐", "누구", "어떤")

    /**
     * 질의 텍스트를 유형으로 분류한다.
     *
     * @param queryText 사용자 질의 원문
     * @return 분류 결과
     */
    fun classify(queryText: String): QueryType {
        val normalized = queryText.trim().lowercase()
        if (normalized.isBlank()) return QueryType.OTHER

        val tokens = tokenize(normalized)
        val hasHowWhy = tokens.any { it in howWhyKeywords }
        val hasWhatWho = tokens.any { it in whatWhoKeywords }
        val hasHowWhyHint = koreanHowWhyHints.any { normalized.contains(it) }
        val hasWhatWhoHint = koreanWhatWhoHints.any { normalized.contains(it) }

        // how/why 신호가 섞여 있으면 설명형 질문으로 우선 분류한다.
        if (hasHowWhy || hasHowWhyHint) {
            return QueryType.HOW_WHY
        }
        if (hasWhatWho) {
            return QueryType.WHAT_WHO
        }

        // 한글 합성어(예: "무엇인지")를 느슨하게 보정한다.
        if (hasWhatWhoHint) {
            return QueryType.WHAT_WHO
        }

        return QueryType.OTHER
    }

    private fun tokenize(text: String): List<String> {
        return text
            .split(Regex("[^a-z0-9가-힣]+"))
            .filter { it.isNotBlank() }
    }
}
