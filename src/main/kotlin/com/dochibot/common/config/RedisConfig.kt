package com.dochibot.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * Redis 설정.
 *
 * - JWT 토큰 블랙리스트/세션 저장소로 사용
 * - 키: String, 값: JSON (ObjectMapper 사용)
 */
@Configuration
class RedisConfig {

    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        return RedisTemplate<String, Any>().apply {
            this.connectionFactory = connectionFactory
            // 키는 String으로 직렬화
            keySerializer = StringRedisSerializer()
            // 값은 JSON으로 직렬화
            valueSerializer = GenericJackson2JsonRedisSerializer()
            hashKeySerializer = StringRedisSerializer()
            hashValueSerializer = GenericJackson2JsonRedisSerializer()
            // 기본 직렬화 설정
            setEnableTransactionSupport(false)
        }
    }
}
