package com.dochibot.feature.retrieval

import com.dochibot.feature.retrieval.eval.Phase2EvalSet
import com.dochibot.feature.retrieval.eval.Phase2EvalValidator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Phase2EvalSetParseTest {
    @Test
    fun `Phase 2 평가셋 샘플 JSON을 파싱하고 스키마를 검증한다`() {
        val objectMapper: ObjectMapper = jacksonObjectMapper()

        val json = javaClass.classLoader
            .getResourceAsStream("eval/phase2_eval_sample.json")
            ?.bufferedReader()
            ?.readText()
            ?: error("Resource not found: eval/phase2_eval_sample.json")

        val parsed: Phase2EvalSet = objectMapper.readValue(json)

        assertEquals(1, parsed.version)
        assertTrue(parsed.items.isNotEmpty())
        assertTrue(Phase2EvalValidator.validate(parsed).isEmpty())
    }
}
