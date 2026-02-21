package com.dochibot.feature.retrieval.application.verify

import com.dochibot.common.config.DochibotRagProperties
import com.dochibot.feature.retrieval.dto.ChunkCandidate
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class DefaultEvidenceVerifierTest {
    @Test
    fun `minTop1FinalScore가 설정되면 top1 finalScore가 낮을 때 insufficient`() {
        val props = DochibotRagProperties(
            verify = DochibotRagProperties.Verify(
                enabled = true,
                minTop1FinalScore = 0.5,
                minTokenCoverage = 0.0,
                maxChunksToCheck = 5,
            )
        )
        val verifier = DefaultEvidenceVerifier(props, QueryTypeClassifier())

        val chunks = listOf(dummyChunk(finalScore = 0.49, text = "aaaa"))
        val result = verifier.verify(queryText = "aaaa", chunks = chunks)

        assertFalse(result.isSufficient)
    }

    @Test
    fun `minTokenCoverage가 설정되면 query 토큰이 포함되지 않을 때 insufficient`() {
        val props = DochibotRagProperties(
            verify = DochibotRagProperties.Verify(
                enabled = true,
                minTop1FinalScore = 0.0,
                minTokenCoverage = 0.5,
                maxChunksToCheck = 5,
            )
        )
        val verifier = DefaultEvidenceVerifier(props, QueryTypeClassifier())

        val chunks = listOf(dummyChunk(finalScore = 1.0, text = "전혀 관련 없는 내용"))
        val result = verifier.verify(queryText = "RAG_TOKEN_001", chunks = chunks)

        assertFalse(result.isSufficient)
    }

    @Test
    fun `minTokenCoverage가 설정되면 query 토큰이 포함될 때 sufficient`() {
        val props = DochibotRagProperties(
            verify = DochibotRagProperties.Verify(
                enabled = true,
                minTop1FinalScore = 0.0,
                minTokenCoverage = 0.5,
                maxChunksToCheck = 5,
            )
        )
        val verifier = DefaultEvidenceVerifier(props, QueryTypeClassifier())

        val chunks = listOf(dummyChunk(finalScore = 1.0, text = "... RAG_TOKEN_001 ..."))
        val result = verifier.verify(queryText = "RAG_TOKEN_001", chunks = chunks)

        assertTrue(result.isSufficient)
    }

    @Test
    fun `minTop1Top2Gap가 설정되면 top1-top2 gap이 작을 때 insufficient`() {
        val props = DochibotRagProperties(
            verify = DochibotRagProperties.Verify(
                enabled = true,
                minTop1FinalScore = 0.0,
                minTop1Top2Gap = 0.2,
                minSameDocSupport = 1,
                minTokenCoverage = 0.0,
                maxChunksToCheck = 5,
            )
        )
        val verifier = DefaultEvidenceVerifier(props, QueryTypeClassifier())

        val docId = UUID.randomUUID()
        val chunks = listOf(
            dummyChunk(finalScore = 0.9, text = "aaaa", documentId = docId),
            dummyChunk(finalScore = 0.8, text = "aaaa", documentId = docId),
        )
        val result = verifier.verify(queryText = "aaaa", chunks = chunks)

        assertFalse(result.isSufficient)
    }

    @Test
    fun `minSameDocSupport가 2 이상이면 top1과 동일 문서 근거가 부족할 때 insufficient`() {
        val props = DochibotRagProperties(
            verify = DochibotRagProperties.Verify(
                enabled = true,
                minTop1FinalScore = 0.0,
                minTop1Top2Gap = 0.0,
                minSameDocSupport = 2,
                minTokenCoverage = 0.0,
                maxChunksToCheck = 5,
            )
        )
        val verifier = DefaultEvidenceVerifier(props, QueryTypeClassifier())

        val docA = UUID.randomUUID()
        val docB = UUID.randomUUID()
        val chunks = listOf(
            dummyChunk(finalScore = 1.0, text = "aaaa", documentId = docA),
            dummyChunk(finalScore = 0.2, text = "aaaa", documentId = docB),
        )
        val result = verifier.verify(queryText = "aaaa", chunks = chunks)

        assertFalse(result.isSufficient)
    }

    @Test
    fun `maxDistinctDocs가 설정되면 상위 후보 문서가 너무 분산될 때 insufficient`() {
        val props = DochibotRagProperties(
            verify = DochibotRagProperties.Verify(
                enabled = true,
                minTop1FinalScore = 0.0,
                minTop1Top2Gap = 0.0,
                minSameDocSupport = 1,
                maxDistinctDocs = 2,
                minTokenCoverage = 0.0,
                maxChunksToCheck = 5,
            )
        )
        val verifier = DefaultEvidenceVerifier(props, QueryTypeClassifier())

        val docA = UUID.randomUUID()
        val docB = UUID.randomUUID()
        val docC = UUID.randomUUID()
        val chunks = listOf(
            dummyChunk(finalScore = 1.0, text = "aaaa", documentId = docA),
            dummyChunk(finalScore = 0.9, text = "aaaa", documentId = docB),
            dummyChunk(finalScore = 0.8, text = "aaaa", documentId = docC),
        )

        val result = verifier.verify(queryText = "aaaa", chunks = chunks)
        assertFalse(result.isSufficient)
    }

    @Test
    fun `WHAT_WHO 질의는 token coverage 임계치가 강화된다`() {
        val props = DochibotRagProperties(
            verify = DochibotRagProperties.Verify(
                enabled = true,
                minTokenCoverage = 0.3,
                maxChunksToCheck = 5,
            )
        )
        val verifier = DefaultEvidenceVerifier(props, QueryTypeClassifier())

        val chunks = listOf(dummyChunk(finalScore = 1.0, text = "정의"))
        val result = verifier.verify(queryText = "무엇 인가", chunks = chunks)

        assertFalse(result.isSufficient)
        assertTrue(result.reason.startsWith("TOKEN_COVERAGE_BELOW_THRESHOLD"))
        assertTrue(result.appliedMinTokenCoverage == 0.4)
        assertTrue(result.queryType == QueryType.WHAT_WHO)
    }

    @Test
    fun `HOW_WHY 질의는 token coverage 임계치가 완화된다`() {
        val props = DochibotRagProperties(
            verify = DochibotRagProperties.Verify(
                enabled = true,
                minTokenCoverage = 0.2,
                maxChunksToCheck = 5,
            )
        )
        val verifier = DefaultEvidenceVerifier(props, QueryTypeClassifier())

        val chunks = listOf(dummyChunk(finalScore = 1.0, text = "어떻게 적용하는지 설명"))
        val result = verifier.verify(queryText = "어떻게", chunks = chunks)

        assertTrue(result.isSufficient)
        assertTrue(result.appliedMinTokenCoverage == 0.1)
        assertTrue(result.queryType == QueryType.HOW_WHY)
    }

    @Test
    fun `consistencyCheckEnabled가 켜지면 버전 충돌 시 insufficient`() {
        val props = DochibotRagProperties(
            verify = DochibotRagProperties.Verify(
                enabled = true,
                consistencyCheckEnabled = true,
                maxChunksToCheck = 5,
            )
        )
        val verifier = DefaultEvidenceVerifier(props, QueryTypeClassifier())

        val docId = UUID.randomUUID()
        val chunks = listOf(
            dummyChunk(finalScore = 1.0, text = "현재 버전 2.0 을 사용합니다.", documentId = docId),
            dummyChunk(finalScore = 0.9, text = "최신 버전 3.0 으로 마이그레이션하세요.", documentId = docId),
        )

        val result = verifier.verify(queryText = "버전 정보 알려줘", chunks = chunks)
        assertFalse(result.isSufficient)
        assertTrue(result.reason.startsWith("CONFLICTING_FACTS"))
    }

    private fun dummyChunk(finalScore: Double, text: String, documentId: UUID = UUID.randomUUID()): ChunkCandidate {
        return ChunkCandidate(
            id = UUID.randomUUID(),
            documentId = documentId,
            documentTitle = "t",
            sectionId = UUID.randomUUID(),
            sectionPath = null,
            text = text,
            page = null,
            distance = null,
            sparseScore = null,
            denseRank = null,
            sparseRank = null,
            rrfScore = 0.0,
            rerankScore = null,
            finalScore = finalScore,
        )
    }
}
