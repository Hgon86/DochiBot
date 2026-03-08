package com.dochibot.feature.chat.application

import com.dochibot.feature.retrieval.dto.ChunkCandidate

/**
 * 채팅 응답 텍스트와 citation 후보를 사용자 표시용으로 정리한다.
 */
object ChatAnswerFormatter {
    private const val MAX_CITATION_COUNT = 6
    private const val MAX_CITATIONS_PER_DOCUMENT = 2
    private val thinkBlockRegex = Regex("""<think\b[^>]*>.*?</think>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val blankLineRegex = Regex("""\n{3,}""")
    private val whitespaceRegex = Regex("""\s+""")

    /**
     * 모델 응답에서 내부 추론 블록을 제거한다.
     */
    fun sanitizeAnswer(answer: String): String {
        return answer
            .replace("\r\n", "\n")
            .replace(thinkBlockRegex, "")
            .replace(blankLineRegex, "\n\n")
            .trim()
    }

    /**
     * citation 표시와 답변 컨텍스트에 사용할 근거 청크를 중복 제거하여 선택한다.
     */
    fun selectEvidenceChunks(chunks: List<ChunkCandidate>, requestedCount: Int): List<ChunkCandidate> {
        if (chunks.isEmpty()) {
            return emptyList()
        }

        val limit = requestedCount.coerceAtLeast(1).coerceAtMost(MAX_CITATION_COUNT)
        val selected = mutableListOf<ChunkCandidate>()
        val countsByDocument = mutableMapOf<java.util.UUID, Int>()
        val seenKeys = mutableSetOf<String>()

        for (chunk in chunks) {
            if (selected.size >= limit) {
                break
            }

            val currentCount = countsByDocument[chunk.documentId] ?: 0
            if (currentCount >= MAX_CITATIONS_PER_DOCUMENT) {
                continue
            }

            val dedupeKey = buildDedupeKey(chunk)
            if (!seenKeys.add(dedupeKey)) {
                continue
            }

            selected += chunk
            countsByDocument[chunk.documentId] = currentCount + 1
        }

        return if (selected.isNotEmpty()) selected else chunks.take(limit)
    }

    private fun buildDedupeKey(chunk: ChunkCandidate): String {
        val section = chunk.sectionPath?.trim()?.lowercase().orEmpty()
        val snippet = whitespaceRegex.replace(chunk.text.trim(), " ").take(180)
        return listOf(
            chunk.documentId.toString(),
            chunk.page?.toString() ?: "-",
            section,
            snippet,
        ).joinToString("|")
    }
}
