package com.dochibot.feature.retrieval.eval

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Phase2EvalValidatorTest {
    @Test
    fun `expected가 비어 있으면 스키마 오류를 반환한다`() {
        val set = Phase2EvalSet(
            version = 1,
            items = listOf(
                Phase2EvalItem(
                    id = "q1",
                    query = "질문",
                    expected = Phase2EvalExpected(),
                )
            ),
        )

        val errors = Phase2EvalValidator.validate(set)
        assertTrue(errors.any { it.contains("chunkIds/documentId/documentTitleContains") })
    }
}
