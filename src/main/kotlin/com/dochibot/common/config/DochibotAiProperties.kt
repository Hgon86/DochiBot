package com.dochibot.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * AI(LLM/Embedding) 관련 런타임 설정.
 *
 * @property embedding 임베딩 모델 설정
 * @property chat 채팅 관련 설정
 */
@ConfigurationProperties(prefix = "dochibot.ai")
data class DochibotAiProperties(
    val embedding: Embedding = Embedding(),
    val chat: Chat = Chat(),
) {
    /**
     * 임베딩 모델 설정.
     *
     * @property model 임베딩 모델명
     * @property dims 임베딩 차원(dims). DB 벡터 컬럼과 반드시 일치해야 한다.
     */
    data class Embedding(
        val model: String = "mxbai-embed-large",
        val dims: Int = 1024,
    )

    /**
     * 채팅 관련 설정.
     *
     * @property memory 대화 메모리 검색 설정
     */
    data class Chat(
        val memory: Memory = Memory(),
    )

    /**
     * 대화 메모리 검색 설정.
     *
     * @property maxMessages 대화 윈도우에 유지할 최대 메시지 수
     */
    data class Memory(
        val maxMessages: Int = 10,
    )
}
