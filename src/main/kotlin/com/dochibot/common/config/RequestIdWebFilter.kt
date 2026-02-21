package com.dochibot.common.config

import com.dochibot.common.util.id.Uuid7Generator
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * 요청/응답에 Request ID를 부여하고 교환 컨텍스트에 저장하는 WebFilter.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdWebFilter : WebFilter {
    /**
     * X-Request-Id 헤더를 설정하고 exchange attribute로 노출한다.
     *
     * @param exchange 현재 요청/응답 교환 컨텍스트
     * @param chain 필터 체인
     * @return 처리 완료 시그널
     */
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val requestId = exchange.request.headers.getFirst(HEADER_REQUEST_ID)
            ?.takeIf { it.isNotBlank() }
            ?: Uuid7Generator.create().toString()

        exchange.attributes[ATTR_REQUEST_ID] = requestId
        exchange.response.headers[HEADER_REQUEST_ID] = requestId
        return chain.filter(exchange)
    }

    companion object {
        /**
         * 요청/응답 헤더 키.
         */
        const val HEADER_REQUEST_ID = "X-Request-Id"

        /**
         * exchange attribute 키.
         */
        const val ATTR_REQUEST_ID = "requestId"
    }
}
