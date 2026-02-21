package com.dochibot.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

/**
 * CORS 설정.
 *
 * Refresh Token을 HttpOnly Cookie로 사용하는 클라이언트가 `credentials: include`로 호출할 수 있도록
 * `allowCredentials=true`를 활성화한다.
 */
@Configuration
class CorsConfig(
    @Value("\${dochibot.cors.allowed-origins:http://localhost:5173}")
    private val allowedOrigins: String
) {
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val origins = allowedOrigins
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val config = CorsConfiguration().apply {
            allowCredentials = true
            allowedOriginPatterns = origins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf(RequestIdWebFilter.HEADER_REQUEST_ID)
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
