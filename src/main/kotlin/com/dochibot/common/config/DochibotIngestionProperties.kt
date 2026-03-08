package com.dochibot.common.config

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * 문서 인제션(임베딩/색인) 처리 설정.
 *
 * @property worker 워커 스케줄링 설정
 * @property chunking 청킹 설정
 */
@Validated
@ConfigurationProperties(prefix = "dochibot.ingestion")
data class DochibotIngestionProperties(
    val worker: Worker = Worker(),
    val chunking: Chunking = Chunking(),
    val content: Content = Content(),
) {
    /**
     * 인제션 워커 스케줄링 설정.
     *
     * @property enabled 워커 활성화 여부
     * @property fixedDelayMs 스케줄 간격(ms)
     * @property maxJobsPerRun 1회 실행 시 처리할 최대 job 개수
     */
    data class Worker(
        val enabled: Boolean = true,
        @field:Positive
        val fixedDelayMs: Long = 3000,
        @field:Min(1)
        val maxJobsPerRun: Int = 1,
    )

    /**
     * 텍스트 청킹 설정.
     *
     * @property chunkSize 청크 최대 길이(문자)
     * @property chunkOverlap 오버랩 길이(문자)
     * @property maxEmbeddingInputChars 임베딩 모델 입력 상한을 고려한 청크 최대 길이(문자)
     */
    data class Chunking(
        @field:Min(200)
        val chunkSize: Int = 1200,
        @field:Min(0)
        val chunkOverlap: Int = 200,
        @field:Min(100)
        val maxEmbeddingInputChars: Int = 400,
    )

    /**
     * 원본 콘텐츠 로딩 관련 설정.
     *
     * @property maxBytes 단일 문서 원본 최대 크기(바이트)
     */
    data class Content(
        @field:Min(1)
        val maxBytes: Long = 50_000_000,
    )
}
