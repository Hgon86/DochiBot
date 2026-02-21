package com.dochibot.feature.retrieval.eval

import com.dochibot.feature.retrieval.dto.ChunkCandidate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class Phase2EvalScorerTest {
    @Test
    fun `chunkIds 기대값이 있으면 chunk id 기준으로 순위를 계산한다`() {
        val c1 = chunk(title = "A")
        val c2 = chunk(title = "B")
        val item = Phase2EvalItem(
            id = "q1",
            query = "테스트",
            expected = Phase2EvalExpected(chunkIds = listOf(c2.id)),
        )

        val result = Phase2EvalScorer.score(item, listOf(c1, c2))
        assertEquals(2, result.rank)
        assertFalse(result.hit1)
        assertTrue(result.hit3)
        assertTrue(result.hit5)
        assertEquals(0.5, result.reciprocalRank)
    }

    @Test
    fun `documentTitleContains 기대값으로 매칭할 수 있다`() {
        val c1 = chunk(title = "인사규정")
        val c2 = chunk(title = "보안정책")
        val item = Phase2EvalItem(
            id = "q2",
            query = "테스트",
            expected = Phase2EvalExpected(documentTitleContains = "보안"),
        )

        val result = Phase2EvalScorer.score(item, listOf(c1, c2))
        assertEquals(2, result.rank)
        assertTrue(result.hit3)
    }

    private fun chunk(title: String): ChunkCandidate {
        return ChunkCandidate(
            id = UUID.randomUUID(),
            documentId = UUID.randomUUID(),
            documentTitle = title,
            sectionId = UUID.randomUUID(),
            text = "본문",
            rrfScore = 0.0,
            finalScore = 0.0,
        )
    }
}
