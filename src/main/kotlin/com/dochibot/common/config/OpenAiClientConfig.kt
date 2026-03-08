package com.dochibot.common.config

import io.netty.channel.ChannelOption
import java.time.Duration
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ReactorClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import reactor.netty.http.client.HttpClient

/**
 * OpenAI 호환 provider 호출용 HTTP 클라이언트 타임아웃을 명시적으로 설정한다.
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.model", name = ["chat"], havingValue = "openai")
class OpenAiClientConfig {

    @Bean("openAiRestClientBuilder")
    fun openAiRestClientBuilder(
        @Value("\${HTTP_CLIENT_CONNECT_TIMEOUT:5s}") connectTimeout: Duration,
        @Value("\${HTTP_CLIENT_READ_TIMEOUT:300s}") readTimeout: Duration,
    ): RestClient.Builder {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout.toMillis().toInt())
            .responseTimeout(readTimeout)

        return RestClient.builder()
            .requestFactory(ReactorClientHttpRequestFactory(httpClient))
    }

    @Bean("openAiWebClientBuilder")
    fun openAiWebClientBuilder(
        @Value("\${HTTP_CLIENT_CONNECT_TIMEOUT:5s}") connectTimeout: Duration,
        @Value("\${HTTP_CLIENT_READ_TIMEOUT:300s}") readTimeout: Duration,
    ): WebClient.Builder {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout.toMillis().toInt())
            .responseTimeout(readTimeout)

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
    }

    @Bean
    fun openAiApi(
        @Value("\${spring.ai.openai.base-url}") baseUrl: String,
        @Value("\${spring.ai.openai.api-key}") apiKey: String,
        @Value("\${spring.ai.openai.chat.completions-path:/chat/completions}") completionsPath: String,
        @Qualifier("openAiRestClientBuilder") restClientBuilder: RestClient.Builder,
        @Qualifier("openAiWebClientBuilder") webClientBuilder: WebClient.Builder,
    ): OpenAiApi {
        return OpenAiApi.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .completionsPath(completionsPath)
            .restClientBuilder(restClientBuilder)
            .webClientBuilder(webClientBuilder)
            .build()
    }
}
