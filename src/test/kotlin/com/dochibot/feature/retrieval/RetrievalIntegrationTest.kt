package com.dochibot.feature.retrieval

import com.dochibot.common.config.DochibotRagProperties
import com.dochibot.feature.retrieval.application.HybridRetrievalService
import com.dochibot.feature.retrieval.application.rerank.HeuristicReranker
import com.dochibot.feature.retrieval.application.rerank.RerankerRouter
import com.dochibot.feature.retrieval.infrastructure.log.RetrievalStructuredLogger
import com.dochibot.feature.retrieval.infrastructure.metrics.RetrievalMetrics
import com.dochibot.feature.retrieval.repository.ChunkRetrievalRepository
import com.dochibot.feature.retrieval.repository.SectionRetrievalRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.r2dbc.spi.ConnectionFactories
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.reactor.awaitSingle
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.r2dbc.core.DatabaseClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetrievalIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("dochibot")
            .withUsername("test")
            .withPassword("test")
    }

    private lateinit var databaseClient: DatabaseClient
    private lateinit var sectionRepo: SectionRetrievalRepository
    private lateinit var chunkRepo: ChunkRetrievalRepository
    private lateinit var hybrid: HybridRetrievalService
    private lateinit var reranker: HeuristicReranker
    private lateinit var rerankerRouter: RerankerRouter
    private val retrievalStructuredLogger = RetrievalStructuredLogger(jacksonObjectMapper())
    private val retrievalMetrics = RetrievalMetrics(SimpleMeterRegistry())

    @BeforeAll
    fun setUp(): Unit = runBlocking {
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        val r2dbcUrl = "r2dbc:postgresql://${postgres.username}:${postgres.password}@${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}"
        val cf = ConnectionFactories.get(r2dbcUrl)
        databaseClient = DatabaseClient.builder().connectionFactory(cf).build()

        sectionRepo = SectionRetrievalRepository(databaseClient)
        chunkRepo = ChunkRetrievalRepository(databaseClient)
        reranker = HeuristicReranker()
        rerankerRouter = RerankerRouter(reranker)
        hybrid = HybridRetrievalService(
            sectionRepo,
            chunkRepo,
            DochibotRagProperties(),
            rerankerRouter,
            retrievalMetrics,
            retrievalStructuredLogger,
        )
    }

    @AfterAll
    fun tearDown() {
        // containers are managed by TestcontainersExtension
    }

    @BeforeEach
    fun clearData(): Unit = runBlocking {
        // 테스트 간 결과 간섭을 피하기 위해 핵심 테이블을 정리한다.
        databaseClient.sql(
            """
            truncate table chunks, sections, document_ingestion_jobs, documents restart identity cascade
            """.trimIndent()
        )
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    @Test
    fun `COMPLETED 문서만 검색 대상이다`(): Unit = runBlocking {
        val completedDocId = UUID.randomUUID()
        val pendingDocId = UUID.randomUUID()
        val completedSectionId = UUID.randomUUID()
        val pendingSectionId = UUID.randomUUID()

        insertDocument(completedDocId, title = "완료 문서", status = "COMPLETED")
        insertDocument(pendingDocId, title = "대기 문서", status = "PENDING")

        insertSection(
            sectionId = completedSectionId,
            documentId = completedDocId,
            heading = "완료 섹션",
            sectionPath = "완료 섹션",
            embedding = unitVectorAt(0),
        )
        insertSection(
            sectionId = pendingSectionId,
            documentId = pendingDocId,
            heading = "대기 섹션",
            sectionPath = "대기 섹션",
            embedding = unitVectorAt(0),
        )

        // 두 문서 모두 동일한 임베딩을 갖지만, COMPLETED만 반환되어야 한다.
        val candidates = sectionRepo.findDenseCandidates(unitVectorAt(0), limit = 10)
        assertTrue(candidates.any { it.documentId == completedDocId })
        assertTrue(candidates.none { it.documentId == pendingDocId })
    }

    @Test
    fun `dense 청크 검색은 cosine distance가 가장 작은 후보가 먼저 나온다`(): Unit = runBlocking {
        val docId = UUID.randomUUID()
        val sectionId = UUID.randomUUID()
        insertDocument(docId, title = "문서", status = "COMPLETED")
        insertSection(
            sectionId = sectionId,
            documentId = docId,
            heading = "섹션",
            sectionPath = "섹션",
            embedding = unitVectorAt(0),
        )

        val chunk1Id = UUID.randomUUID()
        val chunk2Id = UUID.randomUUID()

        // query는 e0와 동일. chunk1은 동일, chunk2는 e1.
        insertChunk(
            chunkId = chunk1Id,
            documentId = docId,
            sectionId = sectionId,
            chunkIndex = 0,
            text = "alpha",
            page = 1,
            embedding = unitVectorAt(0),
        )
        insertChunk(
            chunkId = chunk2Id,
            documentId = docId,
            sectionId = sectionId,
            chunkIndex = 1,
            text = "beta",
            page = 2,
            embedding = unitVectorAt(1),
        )

        val dense = chunkRepo.findDenseCandidatesBySectionIds(
            sectionIds = listOf(sectionId),
            queryEmbedding = unitVectorAt(0),
            limit = 10,
        )

        assertEquals(chunk1Id, dense.first().id)
    }

    @Test
    fun `HybridRetrievalService는 section gate가 비어도 chunk fallback으로 동작한다`(): Unit = runBlocking {
        val docId = UUID.randomUUID()
        val sectionId = UUID.randomUUID()
        insertDocument(docId, title = "문서", status = "COMPLETED")

        // Gate를 비우기 위해 section_embedding을 null로 둔다.
        insertSection(
            sectionId = sectionId,
            documentId = docId,
            heading = "섹션",
            sectionPath = "섹션",
            embedding = null,
        )

        val chunkId = UUID.randomUUID()
        insertChunk(
            chunkId = chunkId,
            documentId = docId,
            sectionId = sectionId,
            chunkIndex = 0,
            text = "unique_token_123 beta gamma",
            page = null,
            embedding = unitVectorAt(2),
        )

        val top = hybrid.retrieveTopChunks(
            queryText = "unique_token_123",
            queryEmbedding = unitVectorAt(2),
            finalTopK = 5,
        )

        assertTrue(top.isNotEmpty())
        assertEquals(chunkId, top.first().id)
        assertEquals("섹션", top.first().sectionPath)
    }

    @Test
    fun `gate sectionsTopM이 작으면 선택된 섹션 범위로 청크 검색이 제한된다`(): Unit = runBlocking {
        val docId = UUID.randomUUID()
        val sectionAId = UUID.randomUUID()
        val sectionBId = UUID.randomUUID()

        insertDocument(docId, title = "문서", status = "COMPLETED")
        insertSection(
            sectionId = sectionAId,
            documentId = docId,
            heading = "A",
            sectionPath = "A",
            embedding = unitVectorAt(0),
        )
        insertSection(
            sectionId = sectionBId,
            documentId = docId,
            heading = "B",
            sectionPath = "B",
            embedding = unitVectorAt(2),
        )

        // query는 e0. 전역 dense 검색에서는 chunkB(e0)가 1등이지만,
        // gate가 sectionA만 선택하면 chunkA만 대상으로 검색해야 한다.
        val chunkAId = UUID.randomUUID()
        val chunkBId = UUID.randomUUID()
        insertChunk(
            chunkId = chunkAId,
            documentId = docId,
            sectionId = sectionAId,
            chunkIndex = 0,
            text = "alpha",
            page = null,
            embedding = unitVectorAt(1),
        )
        insertChunk(
            chunkId = chunkBId,
            documentId = docId,
            sectionId = sectionBId,
            chunkIndex = 0,
            text = "beta",
            page = null,
            embedding = unitVectorAt(0),
        )

        val base = DochibotRagProperties(
            gate = DochibotRagProperties.Gate(
                denseTopK = 10,
                sparseTopK = 10,
                sectionsTopM = 1,
            ),
            retrieval = DochibotRagProperties.Retrieval(
                denseTopK = 10,
                sparseTopK = 10,
            ),
            fusion = DochibotRagProperties.Fusion(rrfK = 60),
            context = DochibotRagProperties.Context(topN = 5),
        )

        val onlyA = HybridRetrievalService(
            sectionRepo,
            chunkRepo,
            base,
            rerankerRouter,
            retrievalMetrics,
            retrievalStructuredLogger,
        )
            .retrieveTopChunks(
                queryText = "no_match_token",
                queryEmbedding = unitVectorAt(0),
                finalTopK = 1,
            )
        assertEquals(chunkAId, onlyA.first().id)

        val includeBoth = HybridRetrievalService(
            sectionRepo,
            chunkRepo,
            base.copy(gate = base.gate.copy(sectionsTopM = 2)),
            rerankerRouter,
            retrievalMetrics,
            retrievalStructuredLogger,
        )
            .retrieveTopChunks(
                queryText = "no_match_token",
                queryEmbedding = unitVectorAt(0),
                finalTopK = 1,
            )
        assertEquals(chunkBId, includeBoth.first().id)
    }

    private suspend fun insertDocument(id: UUID, title: String, status: String) {
        databaseClient.sql(
            """
            insert into documents (id, title, source_type, status)
            values (:id, :title, 'TEXT', :status)
            """.trimIndent()
        )
            .bind("id", id)
            .bind("title", title)
            .bind("status", status)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun insertSection(
        sectionId: UUID,
        documentId: UUID,
        heading: String,
        sectionPath: String,
        embedding: FloatArray?,
    ) {
        val sql = if (embedding != null) {
            """
            insert into sections (id, document_id, parent_id, level, heading, section_path, summary, section_text, section_embedding)
            values (:id, :documentId, null, 1, :heading, :sectionPath, null, null, (:embedding)::vector)
            """.trimIndent()
        } else {
            """
            insert into sections (id, document_id, parent_id, level, heading, section_path, summary, section_text, section_embedding)
            values (:id, :documentId, null, 1, :heading, :sectionPath, null, null, null)
            """.trimIndent()
        }

        var spec = databaseClient.sql(sql)
            .bind("id", sectionId)
            .bind("documentId", documentId)
            .bind("heading", heading)
            .bind("sectionPath", sectionPath)

        if (embedding != null) {
            spec = spec.bind("embedding", toPgVectorLiteral(embedding))
        }

        spec.fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun insertChunk(
        chunkId: UUID,
        documentId: UUID,
        sectionId: UUID,
        chunkIndex: Int,
        text: String,
        page: Int?,
        embedding: FloatArray,
    ) {
        val sql = """
            insert into chunks (id, document_id, section_id, chunk_index, text, page, chunk_embedding)
            values (:id, :documentId, :sectionId, :chunkIndex, :text, :page, (:embedding)::vector)
        """.trimIndent()

        var spec = databaseClient.sql(sql)
            .bind("id", chunkId)
            .bind("documentId", documentId)
            .bind("sectionId", sectionId)
            .bind("chunkIndex", chunkIndex)
            .bind("text", text)
            .bind("embedding", toPgVectorLiteral(embedding))

        spec = if (page != null) {
            spec.bind("page", page)
        } else {
            spec.bindNull("page", Integer::class.java)
        }

        spec.fetch().rowsUpdated().awaitSingle()
    }

    private fun unitVectorAt(index: Int): FloatArray = RetrievalTestUtils.unitVectorAt(index)

    private fun toPgVectorLiteral(embedding: FloatArray): String = RetrievalTestUtils.toPgVectorLiteral(embedding)
}
