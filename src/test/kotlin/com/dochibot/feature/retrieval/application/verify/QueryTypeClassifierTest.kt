package com.dochibot.feature.retrieval.application.verify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QueryTypeClassifierTest {
    private val classifier = QueryTypeClassifier()

    @Test
    fun `정의형 질문은 WHAT_WHO로 분류한다`() {
        assertEquals(QueryType.WHAT_WHO, classifier.classify("RAG가 무엇인가요?"))
    }

    @Test
    fun `설명형 질문은 HOW_WHY로 분류한다`() {
        assertEquals(QueryType.HOW_WHY, classifier.classify("왜 verify가 필요한가요?"))
    }

    @Test
    fun `기타 질문은 OTHER로 분류한다`() {
        assertEquals(QueryType.OTHER, classifier.classify("최근 배포 일정 알려줘"))
    }

    @Test
    fun `어떤 방법 같이 혼합 신호가 있으면 HOW_WHY를 우선한다`() {
        assertEquals(QueryType.HOW_WHY, classifier.classify("어떤 방법으로 재인덱싱하나요?"))
    }

    @Test
    fun `show 같은 영문 단어는 HOW_WHY 오탐을 피한다`() {
        assertEquals(QueryType.OTHER, classifier.classify("show recent logs"))
    }
}
