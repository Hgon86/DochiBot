package com.dochibot.feature.ingestionjob.application

import com.dochibot.common.config.DochibotIngestionProperties
import com.dochibot.common.util.text.SearchableChunkTextCodec
import org.springframework.stereotype.Service

/**
 * 문서 텍스트를 청크 단위로 분할한다.
 *
 * @property ingestionProperties 청킹 설정
 */
@Service
class TextChunkingService(
    private val ingestionProperties: DochibotIngestionProperties,
) {
    private companion object {
        val WHITESPACE_REGEX = Regex("\\s+")
    }

    /**
     * 페이지 텍스트 목록을 청크로 분할한다.
     *
     * @param pages 페이지 단위 텍스트
     * @return 청크 목록
     */
    fun chunk(documentTitle: String, sections: List<ExtractedSection>): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()
        var index = 0
        sections.forEach { section ->
            val normalized = normalizeText(section.text)
            if (normalized.isBlank()) {
                return@forEach
            }

            val baseChunkSize = minOf(
                ingestionProperties.chunking.chunkSize,
                ingestionProperties.chunking.maxEmbeddingInputChars,
            )
            val prefixLength = SearchableChunkTextCodec.prefixLength(documentTitle, section.sectionPath)
            val reservedChars = if (baseChunkSize >= 100) {
                prefixLength.coerceAtMost(baseChunkSize - 50)
            } else {
                0
            }
            val chunkSize = (baseChunkSize - reservedChars).coerceAtLeast(1)
            val overlap = ingestionProperties.chunking.chunkOverlap.coerceAtMost(chunkSize - 1)

            val sectionChunks = splitText(normalized, chunkSize, overlap)
            sectionChunks.forEach { part ->
                chunks.add(
                    TextChunk(
                        index = index,
                        sectionIndex = section.index,
                        sectionPath = section.sectionPath,
                        page = section.page,
                        text = part,
                    )
                )
                index += 1
            }
        }

        return chunks
    }

    private fun normalizeText(text: String): String {
        val sanitized = buildString(text.length) {
            text.forEach { ch ->
                append(if (Character.isISOControl(ch)) ' ' else ch)
            }
        }

        return sanitized.replace(WHITESPACE_REGEX, " ").trim()
    }

    private fun splitText(text: String, chunkSize: Int, overlap: Int): List<String> {
        if (text.length <= chunkSize) {
            return listOf(text)
        }

        val step = (chunkSize - overlap).coerceAtLeast(1)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = (start + chunkSize).coerceAtMost(text.length)
            chunks.add(text.substring(start, end))
            if (end == text.length) {
                break
            }
            start += step
        }
        if (chunks.size >= 2) {
            val last = chunks[chunks.lastIndex]
            val previous = chunks[chunks.lastIndex - 1]
            if (last.length < (chunkSize / 2) && previous.length + last.length <= chunkSize) {
                chunks[chunks.lastIndex - 1] = previous + last
                chunks.removeAt(chunks.lastIndex)
            }
        }
        return chunks
    }
}

/**
 * 문서 청크.
 *
 * @property index 문서 내 청크 순번
 * @property sectionIndex 소속 섹션 순번
 * @property sectionPath 소속 섹션 경로
 * @property page 페이지 번호(PDF만 사용)
 * @property text 청크 본문
 */
data class TextChunk(
    val index: Int,
    val sectionIndex: Int,
    val sectionPath: String,
    val page: Int?,
    val text: String,
)
