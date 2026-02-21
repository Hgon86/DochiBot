package com.dochibot.feature.retrieval.infrastructure.metrics

import com.dochibot.feature.retrieval.application.rerank.RerankModel
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class RetrievalMetricsTest {
    /**
     * 리트리벌/리랭크 지연 메트릭 기록을 검증한다.
     */
    @Test
    fun `지연 시간 메트릭을 기록한다`() {
        val registry = SimpleMeterRegistry()
        val metrics = RetrievalMetrics(registry)

        metrics.recordRetrievalLatency(25)
        metrics.recordRerankLatency(7, RerankModel.HEURISTIC)

        val retrievalTimer = registry.find("retrieval_latency_ms").timer()
        val rerankTimer = registry.find("rerank_latency_ms").tag("model", "HEURISTIC").timer()

        assertNotNull(retrievalTimer)
        assertNotNull(rerankTimer)
        assertEquals(1L, retrievalTimer!!.count())
        assertEquals(1L, rerankTimer!!.count())
        assertTrue(retrievalTimer.totalTime(TimeUnit.MILLISECONDS) >= 25.0)
    }

    /**
     * 후보 개수/점수/verify 결정 메트릭 기록을 검증한다.
     */
    @Test
    fun `카운트와 점수 메트릭을 기록한다`() {
        val registry = SimpleMeterRegistry()
        val metrics = RetrievalMetrics(registry)

        metrics.recordCandidateCount(stage = "chunk_fused", count = 12)
        metrics.recordTop1Score(0.84)
        metrics.recordVerifyDecision("PASS")

        val candidateSummary = registry.find("candidate_count").tag("stage", "chunk_fused").summary()
        val top1Summary = registry.find("top1_score").summary()
        val verifyCounter = registry.find("verify_decision").tag("decision", "PASS").counter()

        assertNotNull(candidateSummary)
        assertNotNull(top1Summary)
        assertNotNull(verifyCounter)
        assertEquals(1L, candidateSummary!!.count())
        assertEquals(12.0, candidateSummary.totalAmount())
        assertEquals(1L, top1Summary!!.count())
        assertEquals(0.84, top1Summary.totalAmount())
        assertEquals(1.0, verifyCounter!!.count())
    }
}
