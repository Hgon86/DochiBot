package com.dochibot.feature.retrieval.mock

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Cross-Encoder endpoint 계약을 검증하기 위한 테스트용 HTTP 서버.
 */
class MockCrossEncoderServer(
    private val handler: (HttpExchange) -> Unit,
) : AutoCloseable {
    private val server: HttpServer = HttpServer.create(InetSocketAddress(0), 0).apply {
        executor = Executors.newSingleThreadExecutor()
        createContext("/rerank") { exchange -> handler(exchange) }
        start()
    }

    val endpoint: String = "http://127.0.0.1:${server.address.port}/rerank"

    override fun close() {
        server.stop(0)
    }

    companion object {
        fun writeJson(exchange: HttpExchange, status: Int = 200, json: String) {
            val bytes = json.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
    }
}
