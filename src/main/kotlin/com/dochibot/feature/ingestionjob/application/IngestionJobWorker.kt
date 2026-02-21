package com.dochibot.feature.ingestionjob.application

import com.dochibot.common.config.DochibotIngestionProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * 문서 인제션 작업을 주기적으로 처리하는 워커.
 *
 * @property ingestionProperties 인제션 워커 설정
 * @property documentIngestionProcessor 인제션 처리 서비스
 */
@Service
class IngestionJobWorker(
    private val ingestionProperties: DochibotIngestionProperties,
    private val documentIngestionProcessor: DocumentIngestionProcessor,
) {
    /**
     * 대기(QUEUED) 작업을 주기적으로 처리한다.
     */
    @Scheduled(fixedDelayString = "\${dochibot.ingestion.worker.fixed-delay-ms:3000}")
    suspend fun poll() {
        if (!ingestionProperties.worker.enabled) {
            return
        }
        documentIngestionProcessor.processBatch(ingestionProperties.worker.maxJobsPerRun)
    }
}
