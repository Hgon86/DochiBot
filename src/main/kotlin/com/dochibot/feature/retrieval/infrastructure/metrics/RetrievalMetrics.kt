package com.dochibot.feature.retrieval.infrastructure.metrics

import com.dochibot.feature.retrieval.application.rerank.RerankModel
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.time.Duration

/**
 * RAG 리트리벌/검증 단계의 핵심 메트릭을 기록한다.
 *
 * @property meterRegistry Micrometer 레지스트리
 */
@Component
class RetrievalMetrics(
    private val meterRegistry: MeterRegistry,
) {
    private val retrievalLatencyTimer: Timer = Timer.builder("retrieval_latency_ms")
        .description("전체 retrieval 처리 지연(ms)")
        .register(meterRegistry)

    private val rerankLatencyTimers: Map<RerankModel, Timer> = RerankModel.entries.associateWith { model ->
        Timer.builder("rerank_latency_ms")
            .description("rerank 처리 지연(ms)")
            .tag("model", model.name)
            .register(meterRegistry)
    }

    private val candidateCountSummaries = ConcurrentHashMap<String, DistributionSummary>()

    private val top1ScoreSummary: DistributionSummary = DistributionSummary.builder("top1_score")
        .description("최종 top1 score")
        .register(meterRegistry)

    /**
     * 리트리벌 전체 지연 시간을 기록한다.
     *
     * @param latencyMs 지연 시간(ms)
     */
    fun recordRetrievalLatency(latencyMs: Long) {
        retrievalLatencyTimer.record(Duration.ofMillis(latencyMs.coerceAtLeast(0)))
    }

    /**
     * 리랭크 지연 시간을 기록한다.
     *
     * @param latencyMs 지연 시간(ms)
     * @param model 리랭크 모델
     */
    fun recordRerankLatency(latencyMs: Long, model: RerankModel) {
        rerankLatencyTimers.getValue(model)
            .record(Duration.ofMillis(latencyMs.coerceAtLeast(0)))
    }

    /**
     * 단계별 후보 개수를 기록한다.
     *
     * @param stage 후보 집계 단계
     * @param count 후보 개수
     */
    fun recordCandidateCount(stage: String, count: Int) {
        val summary = candidateCountSummaries.computeIfAbsent(stage) {
            DistributionSummary.builder("candidate_count")
                .description("단계별 후보 개수")
                .baseUnit("items")
                .tag("stage", it)
                .register(meterRegistry)
        }
        summary
            .record(count.coerceAtLeast(0).toDouble())
    }

    /**
     * 최종 top1 점수를 기록한다.
     *
     * @param score top1 점수
     */
    fun recordTop1Score(score: Double) {
        top1ScoreSummary.record(score)
    }

    /**
     * verify 정책 결정 결과를 카운팅한다.
     *
     * @param decision verify 결정값
     */
    fun recordVerifyDecision(decision: String) {
        meterRegistry.counter("verify_decision", "decision", decision).increment()
    }
}
