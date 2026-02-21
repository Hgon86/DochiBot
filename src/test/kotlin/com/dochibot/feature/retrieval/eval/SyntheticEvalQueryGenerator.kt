package com.dochibot.feature.retrieval.eval

import com.dochibot.feature.retrieval.mock.MockDocumentStore

/**
 * Mock 문서 저장소 기반 합성 평가 질의를 생성한다.
 */
object SyntheticEvalQueryGenerator {

    /**
     * 저장소의 청크 메타데이터를 바탕으로 평가 항목을 생성한다.
     *
     * @param store Mock 문서 저장소
     * @param maxItems 생성 최대 건수
     * @return 합성 평가 항목 목록
     */
    fun generateFromStore(store: MockDocumentStore, maxItems: Int = 50): List<Phase2EvalItem> {
        require(maxItems >= 1) { "maxItems must be >= 1" }

        return store.findAllChunks()
            .asSequence()
            .mapIndexed { index, chunk ->
                val normalizedTitle = chunk.documentTitle.trim().ifBlank { "문서" }
                val normalizedSection = chunk.sectionPath?.trim().orEmpty()
                val query = if (normalizedSection.isNotBlank()) {
                    "$normalizedTitle 문서의 $normalizedSection 내용을 설명해주세요"
                } else {
                    "$normalizedTitle 문서 핵심 내용을 설명해주세요"
                }

                Phase2EvalItem(
                    id = "synthetic-${index + 1}",
                    query = query,
                    expected = Phase2EvalExpected(chunkIds = listOf(chunk.id)),
                    queryType = "synthetic",
                    tags = listOf("synthetic", "auto-generated"),
                    difficulty = "easy",
                    notes = "MockDocumentStore 기반 자동 생성",
                )
            }
            .take(maxItems)
            .toList()
    }
}
