package com.dochibot.e2e

import com.dochibot.domain.entity.User
import com.dochibot.domain.enums.UserRole
import com.dochibot.domain.repository.UserRepository
import com.dochibot.feature.chat.dto.ChatRequest
import com.dochibot.feature.chat.dto.ChatResponse as ApiChatResponse
import com.dochibot.feature.document.dto.CreateDocumentUploadUrlRequest
import com.dochibot.feature.document.dto.CreateDocumentUploadUrlResponse
import com.dochibot.feature.document.dto.FinalizeDocumentUploadRequest
import com.dochibot.feature.document.dto.FinalizeDocumentUploadResponse
import com.dochibot.feature.ingestionjob.application.DocumentContentLoader
import com.dochibot.feature.ingestionjob.application.DocumentIngestionProcessor
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verify가 활성화되어도 기준을 만족하면 정상적으로 LLM 호출이 진행되는지 E2E로 검증한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class RagVerifyPassThroughE2eTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("dochibot")
            .withUsername("test")
            .withPassword("test")

        @Container
        @JvmStatic
        val redis = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}"
            }
            registry.add("spring.r2dbc.username") { postgres.username }
            registry.add("spring.r2dbc.password") { postgres.password }

            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }

            registry.add("spring.flyway.url") { postgres.jdbcUrl }
            registry.add("spring.flyway.user") { postgres.username }
            registry.add("spring.flyway.password") { postgres.password }

            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.firstMappedPort }

            // Verify를 켜되 기본값에 가깝게 두어 충분한 근거가 있으면 통과하도록 한다.
            registry.add("dochibot.rag.verify.enabled") { "true" }
            registry.add("dochibot.rag.verify.policy") { "NO_EVIDENCE" }
            registry.add("dochibot.rag.verify.min-top1-final-score") { "0.0" }
            registry.add("dochibot.rag.verify.min-token-coverage") { "0.0" }
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var jwtEncoder: JwtEncoder

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var ingestionProcessor: DocumentIngestionProcessor

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Autowired
    private lateinit var chatModelCallCounter: AtomicInteger

    @BeforeEach
    fun resetState(): Unit = runBlocking {
        chatModelCallCounter.set(0)

        databaseClient.sql(
            """
            truncate table chat_messages, chat_sessions, chunks, sections, document_ingestion_jobs, documents, users
            restart identity cascade
            """.trimIndent()
        )
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        redisTemplate
            .execute { connection -> connection.serverCommands().flushAll() }
            .collectList()
            .awaitSingle()
    }

    @Test
    fun `verify enabled여도 기준 충족 시 LLM을 호출한다`() = runBlocking {
        val userId = UUID.randomUUID()
        val token = issueToken(userId = userId, role = "ADMIN")

        val user = User(
            id = userId,
            username = "test-admin-$userId",
            role = UserRole.ADMIN,
        )
        user.markAsNew()
        userRepository.save(user)

        val upload = webTestClient
            .post()
            .uri("/api/v1/documents/upload-url")
            .header("Authorization", "Bearer $token")
            .bodyValue(CreateDocumentUploadUrlRequest(originalFilename = "doc.md", contentType = "text/markdown"))
            .exchange()
            .expectStatus().isOk
            .expectBody(CreateDocumentUploadUrlResponse::class.java)
            .returnResult()
            .responseBody!!

        webTestClient
            .post()
            .uri("/api/v1/documents")
            .header("Authorization", "Bearer $token")
            .bodyValue(
                FinalizeDocumentUploadRequest(
                    documentId = upload.documentId,
                    title = "테스트 문서",
                    sourceType = com.dochibot.domain.enums.DocumentSourceType.TEXT,
                    originalFilename = "doc.md",
                    storageUri = upload.storageUri,
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(FinalizeDocumentUploadResponse::class.java)

        ingestionProcessor.processBatch(maxJobs = 1)

        val chatResponse = webTestClient
            .post()
            .uri("/api/v1/chat")
            .header("Authorization", "Bearer $token")
            .bodyValue(ChatRequest(message = "RAG_TOKEN_001 이 뭐야?", topK = 10))
            .exchange()
            .expectStatus().isOk
            .expectBody(ApiChatResponse::class.java)
            .returnResult()
            .responseBody!!

        assertTrue(chatResponse.answer.contains("테스트 답변"))
        assertTrue(chatResponse.citations.isNotEmpty())
        assertEquals(1, chatModelCallCounter.get())
    }

    private fun issueToken(userId: UUID, role: String): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .claim("role", role)
            .build()

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }

    @TestConfiguration
    class TestOverrides {
        @Bean(name = ["chatModelCallCounter"])
        fun chatModelCallCounter(): AtomicInteger = AtomicInteger(0)

        @Bean
        @Primary
        fun embeddingModel(): EmbeddingModel {
            return object : EmbeddingModel {
                override fun call(request: EmbeddingRequest): EmbeddingResponse {
                    val results = request.instructions.mapIndexed { i, text ->
                        val idx = if (text.contains("RAG_TOKEN_001")) 7 else (kotlin.math.abs(text.hashCode()) % 1024)
                        val v = FloatArray(1024)
                        v[idx] = 1.0f
                        Embedding(v, i)
                    }
                    return EmbeddingResponse(results)
                }

                override fun embed(document: Document): FloatArray {
                    val text = document.text ?: ""
                    val idx = if (text.contains("RAG_TOKEN_001")) 7 else (kotlin.math.abs(text.hashCode()) % 1024)
                    val v = FloatArray(1024)
                    v[idx] = 1.0f
                    return v
                }
            }
        }

        @Bean
        @Primary
        fun chatModel(chatModelCallCounter: AtomicInteger): ChatModel {
            return object : ChatModel {
                override fun call(prompt: Prompt): ChatResponse {
                    chatModelCallCounter.incrementAndGet()
                    val msg = AssistantMessage("테스트 답변입니다. [1]")
                    return ChatResponse(listOf(Generation(msg)))
                }
            }
        }

        @Bean
        @Primary
        fun documentContentLoader(): DocumentContentLoader {
            return DocumentContentLoader {
                val bytes = """
                    본 문서는 테스트용이다.
                    RAG_TOKEN_001 이 포함되어 있다.
                    이 토큰은 질의와 매칭되어야 한다.

                    ${"RAG_TOKEN_001 ".repeat(200)}
                """.trimIndent().toByteArray(Charsets.UTF_8)
                bytes
            }
        }
    }
}
