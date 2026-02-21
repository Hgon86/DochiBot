package com.dochibot.feature.retrieval.eval

import com.dochibot.feature.retrieval.mock.MockDocumentStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyntheticEvalQueryGeneratorTest {
    private val objectMapper = jacksonObjectMapper()

    /**
     * Mock 저장소에서 합성 평가 항목이 생성되는지 검증한다.
     */
    @Test
    fun `mock 저장소 기반 합성 질의를 생성한다`() {
        val store = loadStore()

        val items = SyntheticEvalQueryGenerator.generateFromStore(store = store, maxItems = 3)

        assertEquals(3, items.size)
        assertTrue(items.all { it.id.startsWith("synthetic-") })
        assertTrue(items.all { it.expected?.chunkIds?.isNotEmpty() == true })
        assertTrue(items.all { it.query.isNotBlank() })
    }

    /**
     * mock 저장소를 샘플 리소스로부터 생성한다.
     *
     * @return 생성된 MockDocumentStore
     */
    private fun loadStore(): MockDocumentStore {
        val json = javaClass.classLoader
            .getResourceAsStream("eval/phase2_mock_documents_sample.json")
            ?.bufferedReader()
            ?.readText()
            ?: error("Resource not found: eval/phase2_mock_documents_sample.json")
        return MockDocumentStore.fromJson(json, objectMapper)
    }
}
