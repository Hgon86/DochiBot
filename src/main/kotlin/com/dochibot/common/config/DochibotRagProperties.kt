package com.dochibot.common.config

import com.dochibot.feature.retrieval.application.rerank.RerankModel
import com.dochibot.feature.retrieval.application.verify.VerifyPolicy
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * RAG(리트리벌/융합/컨텍스트) 튜닝 파라미터.
 *
 * Phase 1에서는 allowlist 없이 `documents.status = 'COMPLETED'`만 대상으로 검색한다.
 *
 * @property gate Gate 단계(sections) 설정
 * @property retrieval Range-limited 단계(chunks) 설정
 * @property fusion Fusion(RRF) 설정
 * @property context 최종 컨텍스트 구성 설정
 * @property rerank 리랭커 설정
 * @property verify 근거 검증/정책 라우팅 설정
 */
@Validated
@ConfigurationProperties(prefix = "dochibot.rag")
data class DochibotRagProperties(
    @field:Valid
    val gate: Gate = Gate(),
    @field:Valid
    val retrieval: Retrieval = Retrieval(),
    @field:Valid
    val fusion: Fusion = Fusion(),
    @field:Valid
    val context: Context = Context(),
    @field:Valid
    val rerank: Rerank = Rerank(),
    @field:Valid
    val verify: Verify = Verify(),
) {
    /**
     * Gate 단계(sections) 설정.
     *
     * @property denseTopK dense 검색 상위 K개
     * @property sparseTopK sparse 검색 상위 K개
     * @property sectionsTopM 선택할 섹션 최대 개수
     */
    data class Gate(
        @field:Min(1)
        val denseTopK: Int = 20,
        @field:Min(1)
        val sparseTopK: Int = 20,
        @field:Min(1)
        val sectionsTopM: Int = 5,
    )

    /**
     * Range-limited 단계(chunks) 설정.
     *
     * @property denseTopK dense 검색 상위 K개
     * @property sparseTopK sparse 검색 상위 K개
     */
    data class Retrieval(
        @field:Min(1)
        val denseTopK: Int = 50,
        @field:Min(1)
        val sparseTopK: Int = 50,
    )

    /**
     * Fusion(RRF) 설정.
     *
     * @property rrfK RRF 상수 (보통 60 사용)
     */
    data class Fusion(
        @field:Min(1)
        val rrfK: Int = 60,
    )

    /**
     * 최종 컨텍스트 구성 설정.
     *
     * @property topN 최종 선택할 청크 개수
     */
    data class Context(
        @field:Min(1)
        val topN: Int = 8,
    )

    /**
     * 리랭커 설정.
     *
     * @property enabled 리랭크 활성화 여부
     * @property candidatesTopK 리랭크 대상 후보 개수
     * @property model 사용할 리랭커 모델
     */
    data class Rerank(
        val enabled: Boolean = false,
        @field:Min(1)
        val candidatesTopK: Int = 100,
        val model: RerankModel = RerankModel.HEURISTIC,
        @field:Valid
        val llmJudge: LlmJudge = LlmJudge(),
        @field:Valid
        val crossEncoder: CrossEncoder = CrossEncoder(),
    )

    /**
     * LLM judge 리랭커 설정.
     *
     * @property maxCandidates LLM에 전달할 후보 개수 상한
     * @property timeoutMs 요청 타임아웃(ms)
     * @property snippetChars 후보 청크에서 잘라낼 본문 길이(문자 단위)
     * @property maxAttempts LLM judge 재시도 횟수(파싱 실패/호출 오류 포함)
     * @property fallbackScore 파싱 실패 후보에 부여할 기본 점수
     * @property ensembleCalls 동일 입력 다회 호출 횟수(중앙값 집계)
     */
    data class LlmJudge(
        @field:Min(1)
        val maxCandidates: Int = 20,
        @field:Min(1)
        val timeoutMs: Long = 3000,
        @field:Min(50)
        val snippetChars: Int = 400,
        @field:Min(1)
        val maxAttempts: Int = 2,
        @field:DecimalMin("0.0")
        @field:DecimalMax("1.0")
        val fallbackScore: Double = 0.5,
        @field:Min(1)
        val ensembleCalls: Int = 1,
    )

    /**
     * Cross-Encoder 리랭커 설정(외부 모델 서빙 연동).
     *
     * @property endpoint Cross-Encoder 점수 계산 엔드포인트 URL(미설정 시 fallback)
     * @property apiKey Cross-Encoder endpoint 인증 키(선택)
     * @property timeoutMs 요청 타임아웃(ms)
     * @property maxCandidates 엔드포인트로 전달할 후보 개수 상한
     * @property snippetChars 후보 청크에서 잘라낼 본문 길이(문자 단위)
     */
    data class CrossEncoder(
        val endpoint: String = "",
        val apiKey: String = "",
        @field:Min(1)
        val timeoutMs: Long = 2000,
        @field:Min(1)
        val maxCandidates: Int = 50,
        @field:Min(50)
        val snippetChars: Int = 400,
    )

    /**
     * 근거 검증/정책 라우팅 설정.
     *
     * - enabled=false면 기존과 동일하게, 청크가 존재하면 LLM을 호출한다.
     * - enabled=true면 최소 기준을 만족하지 않을 때 LLM 호출을 차단하고 정책 응답을 반환한다.
     *
     * @property enabled 검증 활성화 여부
     * @property policy 근거 부족 시 정책 응답 전략
     * @property maxChunksToCheck 토큰 커버리지 계산에 사용할 상위 청크 개수
     * @property minTop1FinalScore top1의 finalScore 최소값(0이면 비활성)
     * @property minTop1Top2Gap top1과 top2 점수 차이 최소값(0이면 비활성)
     * @property minSameDocSupport top1과 동일 문서 근거 최소 개수(1이면 비활성)
     * @property maxDistinctDocs 상위 청크 내 최대 문서 개수(0이면 비활성)
     * @property minTokenCoverage query 토큰 커버리지 최소값(0이면 비활성)
     * @property consistencyCheckEnabled 상위 청크 간 간단한 사실 충돌 검사 활성화 여부
     */
    data class Verify(
        val enabled: Boolean = false,
        val policy: VerifyPolicy = VerifyPolicy.NO_EVIDENCE,
        @field:Min(1)
        val maxChunksToCheck: Int = 5,
        @field:PositiveOrZero
        val minTop1FinalScore: Double = 0.0,
        @field:PositiveOrZero
        val minTop1Top2Gap: Double = 0.0,
        @field:Min(1)
        val minSameDocSupport: Int = 1,
        @field:PositiveOrZero
        val maxDistinctDocs: Int = 0,
        @field:DecimalMin("0.0")
        @field:DecimalMax("1.0")
        val minTokenCoverage: Double = 0.0,
        val consistencyCheckEnabled: Boolean = false,
    )
}
