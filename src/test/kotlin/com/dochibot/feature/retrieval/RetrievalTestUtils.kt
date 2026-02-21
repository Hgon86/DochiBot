package com.dochibot.feature.retrieval

/**
 * Retrieval 테스트 유틸리티.
 *
 * 통합 테스트와 벤치마크 테스트에서 공통으로 사용하는 임베딩/벡터 변환 함수들.
 */
object RetrievalTestUtils {

    /**
     * 지정된 인덱스만 1.0이고 나머지는 0.0인 단위 벡터를 생성한다.
     *
     * @param index 1.0으로 설정할 인덱스 (0부터 시작)
     * @param size 벡터 크기 (기본 1024)
     * @return 단위 벡터
     */
    fun unitVectorAt(index: Int, size: Int = 1024): FloatArray {
        require(index in 0 until size) { "index must be in range [0, $size)" }
        return FloatArray(size).apply { this[index] = 1.0f }
    }

    /**
     * FloatArray를 PostgreSQL vector 타입 리터럴 문자열로 변환한다.
     *
     * @param embedding 변환할 임베딩 배열
     * @return "[1.0,0.0,...]" 형식의 문자열
     */
    fun toPgVectorLiteral(embedding: FloatArray): String = buildString {
        append('[')
        embedding.forEachIndexed { i, value ->
            if (i > 0) append(',')
            append(value)
        }
        append(']')
    }
}
