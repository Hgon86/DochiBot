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

    /**
     * 스트리밍 응답에서 think 태그를 제거하며 사용자에게 보여줄 텍스트만 누적한다.
     */
    class StreamingAnswerSanitizer {
        private val buffer = StringBuilder()
        private var insideThinkBlock = false

        fun consume(chunk: String): String {
            buffer.append(chunk)
            return drain(flushAll = false)
        }

        fun finish(): String {
            return drain(flushAll = true)
        }

        private fun drain(flushAll: Boolean): String {
            val visible = StringBuilder()

            while (buffer.isNotEmpty()) {
                if (insideThinkBlock) {
                    val closeIdx = buffer.indexOf("</think>", ignoreCase = true)
                    if (closeIdx >= 0) {
                        buffer.delete(0, closeIdx + "</think>".length)
                        insideThinkBlock = false
                        continue
                    }

                    if (flushAll) {
                        buffer.clear()
                    } else {
                        val keep = "</think>".length - 1
                        if (buffer.length > keep) {
                            buffer.delete(0, buffer.length - keep)
                        }
                    }
                    break
                }

                val openIdx = buffer.indexOf("<think", ignoreCase = true)
                if (openIdx >= 0) {
                    if (openIdx > 0) {
                        visible.append(buffer.substring(0, openIdx))
                        buffer.delete(0, openIdx)
                    }

                    val openEndIdx = buffer.indexOf(">")
                    if (openEndIdx >= 0) {
                        buffer.delete(0, openEndIdx + 1)
                        insideThinkBlock = true
                        continue
                    }
                    break
                }

                if (flushAll) {
                    visible.append(buffer)
                    buffer.clear()
                    break
                }

                val keep = "<think".length - 1
                if (buffer.length > keep) {
                    val emitLength = buffer.length - keep
                    visible.append(buffer.substring(0, emitLength))
                    buffer.delete(0, emitLength)
                }
                break
            }

            return visible.toString()
        }

        private fun StringBuilder.indexOf(value: String, ignoreCase: Boolean): Int {
            return toString().indexOf(value, ignoreCase = ignoreCase)
        }
    }
}
