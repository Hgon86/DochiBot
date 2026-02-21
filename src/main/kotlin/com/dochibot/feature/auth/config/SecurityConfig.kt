package com.dochibot.feature.auth.config

import com.dochibot.common.config.RequestIdWebFilter
import com.dochibot.common.exception.ApiErrorResponse
import com.dochibot.common.exception.CommonErrorCode
import com.dochibot.common.exception.ErrorI18nSupport
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType

import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.AuthenticationException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler
import org.springframework.web.cors.reactive.CorsConfigurationSource
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.springframework.web.server.ServerWebExchange
import com.nimbusds.jose.jwk.source.ImmutableSecret

/**
 * Spring Security 설정 클래스.
 *
 * JWT 기반 OAuth2 리소스 서버 구성 및 API 접근 권한을 설정합니다.
 */
@Configuration
class SecurityConfig(
    @Value("\${dochibot.jwt.secret:}")
    private val jwtSecret: String,
    private val objectMapper: ObjectMapper,
    private val errorI18nSupport: ErrorI18nSupport,
    private val environment: Environment
) {
    private val log = KotlinLogging.logger {}

    /**
     * 비밀번호 인코더 Bean.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    /**
     * JWT 서명용 SecretKey Bean.
     */
    @Bean
    fun jwtSecretKey(): SecretKey {
        if (jwtSecret.isBlank()) {
            val profiles = environment.activeProfiles.toSet()
            if (profiles.contains("local") || profiles.contains("docker")) {
                log.warn { "JWT_SECRET (or dochibot.jwt.secret) is blank; using an ephemeral key (local/docker only)" }
                return generateEphemeralSecretKey()
            }

            throw IllegalStateException("JWT_SECRET (or dochibot.jwt.secret) is required")
        }

        val bytes = jwtSecret.toByteArray(StandardCharsets.UTF_8)
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    private fun generateEphemeralSecretKey(): SecretKey {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    /**
     * JWT 인코더 Bean.
     */
    @Bean
    fun jwtEncoder(secretKey: SecretKey): JwtEncoder {
        return NimbusJwtEncoder(ImmutableSecret(secretKey.encoded))
    }

    /**
     * JWT 디코더 Bean.
     */
    @Bean
    fun jwtDecoder(secretKey: SecretKey): ReactiveJwtDecoder {
        return NimbusReactiveJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
    }

    /**
     * Spring Security 웹 필터 체인 Bean.
     *
     * 경로별 접근 권한과 JWT 인증을 설정한다.
     */
    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        jwtDecoder: ReactiveJwtDecoder,
        corsConfigurationSource: CorsConfigurationSource
    ): SecurityWebFilterChain {
        val jwtAuthenticationConverter = ReactiveJwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter { jwt ->
                val role = jwt.getClaimAsString("role")
                val authorities: List<GrantedAuthority> = if (role.isNullOrBlank()) {
                    emptyList()
                } else {
                    listOf(SimpleGrantedAuthority("ROLE_$role"))
                }
                Flux.fromIterable(authorities)
            }
        }

        return http
            .cors { it.configurationSource(corsConfigurationSource) }
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .exceptionHandling { handlers ->
                handlers.authenticationEntryPoint(JsonAuthenticationEntryPoint(objectMapper, errorI18nSupport))
                handlers.accessDeniedHandler(JsonAccessDeniedHandler(objectMapper, errorI18nSupport))
            }
            .authorizeExchange { exchanges ->
                exchanges.pathMatchers(HttpMethod.OPTIONS).permitAll()
                exchanges.pathMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout",
                    "/api/v1/auth/oauth2/**",
                    "/api/v1/health"
                ).permitAll()
                exchanges.pathMatchers("/api/v1/documents/**", "/api/v1/ingestion-jobs/**").hasRole("ADMIN")
                exchanges.pathMatchers("/api/v1/chat/**").hasAnyRole("USER", "ADMIN")
                exchanges.anyExchange().authenticated()
            }
            .oauth2ResourceServer { oauth ->
                oauth.jwt { jwt ->
                    jwt.jwtDecoder(jwtDecoder)
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
                }
            }
            .build()
    }

    /**
     * 인증 예외 발생 시 JSON 응답을 반환하는 진입점.
     */
    private class JsonAuthenticationEntryPoint(
        private val objectMapper: ObjectMapper,
        private val errorI18nSupport: ErrorI18nSupport
    ) : ServerAuthenticationEntryPoint {
        override fun commence(exchange: ServerWebExchange, ex: AuthenticationException): Mono<Void> {
            val authHeader = exchange.request.headers.getFirst("Authorization")
            val errorCode = if (authHeader.isNullOrBlank()) {
                CommonErrorCode.AUTH_REQUIRED
            } else {
                CommonErrorCode.AUTH_INVALID_TOKEN
            }
            return write(exchange, errorCode)
        }

        private fun write(exchange: ServerWebExchange, errorCode: CommonErrorCode): Mono<Void> {
            val traceId = exchange.getAttribute<String>(RequestIdWebFilter.ATTR_REQUEST_ID)
                ?: exchange.request.headers.getFirst(RequestIdWebFilter.HEADER_REQUEST_ID)

            val code = errorCode.code
            val status = errorCode.status
            val body = ApiErrorResponse(
                status = status.value(),
                code = code,
                message = errorI18nSupport.defaultEnglishMessage(code),
                i18nKey = errorI18nSupport.i18nKey(code),
                traceId = traceId
            )

            exchange.response.statusCode = status
            exchange.response.headers.contentType = MediaType.APPLICATION_JSON
            if (!traceId.isNullOrBlank()) {
                exchange.response.headers[RequestIdWebFilter.HEADER_REQUEST_ID] = traceId
            }

            val bytes = objectMapper.writeValueAsBytes(body)
            val buffer = exchange.response.bufferFactory().wrap(bytes)
            return exchange.response.writeWith(Mono.just(buffer))
        }
    }

    /**
     * 권한 거부 예외 발생 시 JSON 응답을 반환하는 핸들러.
     */
    private class JsonAccessDeniedHandler(
        private val objectMapper: ObjectMapper,
        private val errorI18nSupport: ErrorI18nSupport
    ) : ServerAccessDeniedHandler {
        override fun handle(exchange: ServerWebExchange, denied: AccessDeniedException): Mono<Void> {
            val traceId = exchange.getAttribute<String>(RequestIdWebFilter.ATTR_REQUEST_ID)
                ?: exchange.request.headers.getFirst(RequestIdWebFilter.HEADER_REQUEST_ID)

            val code = CommonErrorCode.AUTH_FORBIDDEN.code
            val status = CommonErrorCode.AUTH_FORBIDDEN.status
            val body = ApiErrorResponse(
                status = status.value(),
                code = code,
                message = errorI18nSupport.defaultEnglishMessage(code),
                i18nKey = errorI18nSupport.i18nKey(code),
                traceId = traceId
            )

            exchange.response.statusCode = status
            exchange.response.headers.contentType = MediaType.APPLICATION_JSON
            if (!traceId.isNullOrBlank()) {
                exchange.response.headers[RequestIdWebFilter.HEADER_REQUEST_ID] = traceId
            }

            val bytes = objectMapper.writeValueAsBytes(body)
            val buffer = exchange.response.bufferFactory().wrap(bytes)
            return exchange.response.writeWith(Mono.just(buffer))
        }
    }
}
