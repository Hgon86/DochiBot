package com.dochibot.feature.health.controller

import com.dochibot.feature.health.dto.HealthResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 서비스 상태 확인 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/health")
class HealthController {
    /**
     * 서비스 상태를 반환한다.
     *
     * @return 서비스 상태
     */
    @GetMapping
    suspend fun health(): HealthResponse {
        return HealthResponse()
    }
}
