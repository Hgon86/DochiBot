package com.dochibot.feature.auth.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class OAuth2ClientConfig {

    @Bean
    fun oauth2WebClient(): WebClient {
        return WebClient.builder()
            .codecs { config ->
                config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)
            }
            .build()
    }
}