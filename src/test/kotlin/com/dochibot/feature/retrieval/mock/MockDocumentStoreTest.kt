package com.dochibot.feature.retrieval.mock

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class MockDocumentStoreTest {
    private val objectMapper = jacksonObjectMapper()

    /**
     * Mock 샘플 데이터셋을 로딩하고 ID 조회가 가능한지 검증한다.
     */
    @Test
    fun `mock 저장소가 chunk id 조회를 지원한다`() {
        val store = loadStore()
        val knownChunkId = UUID.fromString("f447d5db-535f-4f71-bd8c-b1c8d2ea1c31")

        val chunk = store.findChunkById(knownChunkId)

        assertNotNull(chunk)
        assertEquals(knownChunkId, chunk!!.id)
        assertEquals("문서 업로드 가이드", chunk.documentTitle)
    }

    /**
     * 질의 토큰 오버랩 기반 검색이 기대 문서를 상위로 반환하는지 검증한다.
     */
    @Test
    fun `mock 저장소 검색이 관련 청크를 상위로 반환한다`() {
        val store = loadStore()

        val top = store.retrieveTopChunks(query = "JWT 토큰 만료", topK = 1)

        assertEquals(1, top.size)
        assertTrue(top.first().documentTitle.contains("JWT"))
        assertTrue(top.first().finalScore > 0.0)
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
