package com.dochibot.e2e

import com.dochibot.domain.entity.User
import com.dochibot.domain.enums.UserRole
import com.dochibot.domain.repository.UserRepository
import com.dochibot.domain.repository.ChatMessageRepository
import com.dochibot.domain.repository.ChatSessionRepository
import com.dochibot.feature.chat.dto.ChatRequest
import com.dochibot.feature.document.dto.CreateDocumentUploadUrlRequest
import com.dochibot.feature.document.dto.CreateDocumentUploadUrlResponse
import com.dochibot.feature.document.dto.FinalizeDocumentUploadRequest
import com.dochibot.feature.document.dto.FinalizeDocumentUploadResponse
import com.dochibot.feature.ingestionjob.application.DocumentIngestionProcessor
import com.dochibot.feature.ingestionjob.application.DocumentContentLoader
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.document.Document
import kotlinx.coroutines.flow.toList
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.r2dbc.core.DatabaseClient
import kotlinx.coroutines.reactor.awaitSingle
import java.util.concurrent.atomic.AtomicInteger

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class RagE2eSmokeTest {

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

            // Verify는 기본적으로 비활성화(기존 정책: 청크가 있으면 LLM 호출)
            registry.add("dochibot.rag.verify.enabled") { "false" }
        }
    }

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var jwtEncoder: JwtEncoder

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepository

    @Autowired
    private lateinit var chatMessageRepository: ChatMessageRepository

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
    fun `업로드 URL 발급 - finalize - 인제션 - 채팅까지 스모크`() = runBlocking {
        val userId = UUID.randomUUID()
        val token = issueToken(userId = userId, role = "ADMIN")

        // FK(users) 때문에 문서 생성 전에 user row를 만든다.
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

        val documentText = """
            본 문서는 테스트용이다.
            RAG_TOKEN_001 이 포함되어 있다.
            이 토큰은 질의와 매칭되어야 한다.
        """.trimIndent()

        val finalized = webTestClient
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
            .returnResult()
            .responseBody!!

        assertTrue(finalized.documentId == upload.documentId)

        // 인제션 실행(스케줄러 대신 테스트에서 직접 실행)
        ingestionProcessor.processBatch(maxJobs = 1)

        val chatResponse = webTestClient
            .streamChat(
                token = token,
                request = ChatRequest(message = "RAG_TOKEN_001 이 뭐야?", topK = 50),
            )

        assertTrue(chatResponse.answer.isNotBlank())
        assertTrue(chatResponse.citations.isNotEmpty())
        assertTrue(chatResponse.citations.size <= 2)
        assertEquals("테스트 문서", chatResponse.citations.first().documentTitle)
        assertEquals(1, chatModelCallCounter.get())

        // citations_json 영속화 검증
        val session = chatSessionRepository.findByExternalSessionKey(chatResponse.sessionId)
        assertNotNull(session)
        val messages = chatMessageRepository
            .findByChatSessionIdOrderByCreatedAtAsc(session!!.id)
            .toList()
        val last = messages.last()
        assertEquals(com.dochibot.domain.enums.ChatRole.ASSISTANT, last.role)
        assertNotNull(last.citationsJson)
        assertTrue(last.citationsJson!!.contains("\"documentTitle\":\"테스트 문서\""))
    }

    @Test
    fun `근거가 없으면 모델 호출 없이 정책 응답을 반환한다`() = runBlocking {
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

        // 인제션을 수행하지 않아 chunks가 없으므로, 근거 없음 정책이 동작해야 한다.
        val chatResponse = webTestClient
            .streamChat(
                token = token,
                request = ChatRequest(message = "이 문서의 핵심 요약은 뭐야?", topK = 5),
            )

        assertEquals("문서에서 찾을 수 없습니다.", chatResponse.answer)
        assertTrue(chatResponse.citations.isEmpty())
        assertEquals(0, chatModelCallCounter.get())
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
                    // 실제 LLM 호출 대신, 항상 근거 번호를 포함한 더미 답변을 반환한다.
                    val msg = AssistantMessage("테스트 답변입니다. [1]")
                    return ChatResponse(listOf(Generation(msg)))
                }
            }
        }

        @Bean
        @Primary
        fun documentContentLoader(): DocumentContentLoader {
            return DocumentContentLoader {
                // 업로드된 오브젝트를 실제로 받지 않기 때문에, 고정 콘텐츠를 반환한다.
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
