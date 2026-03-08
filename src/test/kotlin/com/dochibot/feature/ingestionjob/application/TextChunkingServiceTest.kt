package com.dochibot.feature.ingestionjob.application

import com.dochibot.common.config.DochibotIngestionProperties
import com.dochibot.common.util.text.SearchableChunkTextCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextChunkingServiceTest {
    private val service = TextChunkingService(
        DochibotIngestionProperties(
            chunking = DochibotIngestionProperties.Chunking(
                chunkSize = 1200,
                chunkOverlap = 200,
                maxEmbeddingInputChars = 400,
            )
        )
    )

    @Test
    fun `db에 저장할 수 없는 제어문자를 제거한다`() {
        val chunks = service.chunk(
            documentTitle = "테스트 문서",
            sections = listOf(
                ExtractedSection(
                    index = 0,
                    parentIndex = null,
                    level = 1,
                    heading = "본문",
                    sectionPath = "테스트 문서 > 본문",
                    page = 1,
                    text = "PDF\u0000 본문\u0007 에서\n제어문자\r\n를 제거한다.",
                )
            ),
        )

        assertEquals(1, chunks.size)
        assertEquals("PDF 본문 에서 제어문자 를 제거한다.", chunks.single().text)
    }

    @Test
    fun `임베딩 입력 상한을 넘지 않도록 청크 길이를 제한한다`() {
        val chunks = service.chunk(
            documentTitle = "테스트 문서",
            sections = listOf(
                ExtractedSection(
                    index = 0,
                    parentIndex = null,
                    level = 2,
                    heading = "Tech Stack",
                    sectionPath = "테스트 문서 > Tech Stack",
                    page = 1,
                    text = "가".repeat(850),
                )
            ),
        )

        assertTrue(chunks.size >= 3)
        assertTrue(
            chunks.all {
                SearchableChunkTextCodec.encode(
                    documentTitle = "테스트 문서",
                    sectionPath = it.sectionPath,
                    bodyText = it.text,
                ).length <= 400
            }
        )
    }
}
