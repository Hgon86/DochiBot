package com.dochibot.feature.retrieval.application.rerank

import org.springframework.stereotype.Component

/**
 * 리랭커 모델 타입에 따라 구현체를 선택한다.
 *
 * @param heuristicReranker 휴리스틱 리랭커
 * @param llmJudgeReranker LLM judge 리랭커
 * @param crossEncoderReranker Cross-Encoder 리랭커
 */
@Component
class RerankerRouter(
    private val heuristicReranker: HeuristicReranker,
    private val llmJudgeReranker: LlmJudgeReranker? = null,
    private val crossEncoderReranker: CrossEncoderReranker? = null,
) {
    /**
     * 모델 타입에 따라 적절한 리랭커를 선택하여 리랭크를 수행한다.
     *
     * Cross-Encoder/LLM judge 구현체가 주입되지 않거나 설정이 비어 있으면 HEURISTIC으로 fallback한다.
     *
     * @param model 선택할 리랭커 타입
     * @param input 질의/후보 입력
     * @return 점수 내림차순으로 정렬된 리랭크 결과
     * @throws IllegalArgumentException 지원하지 않는 모델 타입인 경우
     */
    suspend fun rerank(model: RerankModel, input: RerankInput): List<RerankedChunk> {
        return when (model) {
            RerankModel.HEURISTIC -> heuristicReranker.rerank(input)
            RerankModel.LLM_JUDGE -> llmJudgeReranker?.rerank(input) ?: heuristicReranker.rerank(input)
            RerankModel.CROSS_ENCODER -> crossEncoderReranker?.rerank(input) ?: heuristicReranker.rerank(input)
        }
    }
}
