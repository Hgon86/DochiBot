package com.dochibot.feature.chat.application

import com.dochibot.feature.retrieval.dto.ChunkCandidate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.UUID

class ChatAnswerFormatterTest {
    @Test
    fun `think 블록을 제거하고 최종 답변만 남긴다`() {
        val answer = ChatAnswerFormatter.sanitizeAnswer(
            """
            <think>
            내부 추론
            </think>

            프로젝트 관련 문서는 README와 이력서입니다.
            """.trimIndent(),
        )

        assertEquals("프로젝트 관련 문서는 README와 이력서입니다.", answer)
    }

    @Test
    fun `같은 문서의 중복 citation 후보를 정리한다`() {
        val documentId = UUID.randomUUID()
        val chunks = listOf(
            chunk(documentId = documentId, page = 1, sectionPath = "intro", text = "중복 본문\n테스트"),
            chunk(documentId = documentId, page = 1, sectionPath = "intro", text = "중복 본문 테스트"),
            chunk(documentId = documentId, page = 2, sectionPath = "usage", text = "다른 페이지 본문"),
        )

        val selected = ChatAnswerFormatter.selectEvidenceChunks(chunks, requestedCount = 3)

        assertEquals(2, selected.size)
        assertEquals(listOf(1, 2), selected.map { it.page })
    }

    @Test
    fun `문서당 citation 수를 제한하고 전체 순서를 유지한다`() {
        val firstDocumentId = UUID.randomUUID()
        val secondDocumentId = UUID.randomUUID()
        val chunks = listOf(
            chunk(documentId = firstDocumentId, page = 1, sectionPath = "a", text = "a-1"),
            chunk(documentId = firstDocumentId, page = 2, sectionPath = "b", text = "a-2"),
            chunk(documentId = firstDocumentId, page = 3, sectionPath = "c", text = "a-3"),
            chunk(documentId = secondDocumentId, page = 1, sectionPath = "a", text = "b-1"),
        )

        val selected = ChatAnswerFormatter.selectEvidenceChunks(chunks, requestedCount = 4)

        assertEquals(3, selected.size)
        assertEquals(listOf("a-1", "a-2", "b-1"), selected.map { it.text })
        assertFalse(selected.any { it.text == "a-3" })
    }

    private fun chunk(
        documentId: UUID,
        page: Int?,
        sectionPath: String,
        text: String,
    ): ChunkCandidate {
        return ChunkCandidate(
            id = UUID.randomUUID(),
            documentId = documentId,
            documentTitle = "테스트 문서",
            sectionId = UUID.randomUUID(),
            sectionPath = sectionPath,
            text = text,
            page = page,
            finalScore = 1.0,
        )
    }
}
