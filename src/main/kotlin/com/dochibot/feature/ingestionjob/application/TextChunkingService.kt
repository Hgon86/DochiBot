package com.dochibot.feature.ingestionjob.application

import com.dochibot.common.config.DochibotIngestionProperties
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
    /**
     * 페이지 텍스트 목록을 청크로 분할한다.
     *
     * @param pages 페이지 단위 텍스트
     * @return 청크 목록
     */
    fun chunk(pages: List<ExtractedPage>): List<TextChunk> {
        val chunkSize = ingestionProperties.chunking.chunkSize
        val overlap = ingestionProperties.chunking.chunkOverlap.coerceAtMost(chunkSize - 1)

        val chunks = mutableListOf<TextChunk>()
        var index = 0
        pages.forEach { page ->
            val normalized = normalizeText(page.text)
            if (normalized.isBlank()) {
                return@forEach
            }

            val pageChunks = splitText(normalized, chunkSize, overlap)
            pageChunks.forEach { part ->
                chunks.add(
                    TextChunk(
                        index = index,
                        page = page.page,
                        text = part,
                    )
                )
                index += 1
            }
        }

        return chunks
    }

    private fun normalizeText(text: String): String {
        return text.replace(Regex("\\s+"), " ").trim()
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
            if (last.length < (chunkSize / 2)) {
                chunks[chunks.lastIndex - 1] = chunks[chunks.lastIndex - 1] + last
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
 * @property page 페이지 번호(PDF만 사용)
 * @property text 청크 텍스트
 */
data class TextChunk(
    val index: Int,
    val page: Int?,
    val text: String,
)
