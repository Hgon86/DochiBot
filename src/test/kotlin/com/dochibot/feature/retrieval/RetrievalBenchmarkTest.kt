package com.dochibot.feature.retrieval

import com.dochibot.common.config.DochibotRagProperties
import com.dochibot.feature.retrieval.application.HybridRetrievalService
import com.dochibot.feature.retrieval.application.rerank.HeuristicReranker
import com.dochibot.feature.retrieval.application.rerank.RerankModel
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.r2dbc.core.DatabaseClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * 수동 실행 벤치마크를 위한 파라미터 스윕 테스트.
 */
@EnabledIfSystemProperty(named = "dochibot.benchmark", matches = "true")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetrievalBenchmarkTest {



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
    }

    @BeforeEach
    fun reset(): Unit = runBlocking {
        databaseClient.sql(
            """
            truncate table chunks, sections, document_ingestion_jobs, documents restart identity cascade
            """.trimIndent()
        )
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        seedDataset()
    }

    /**
     * 파라미터 스윕을 수행하고 Hit@1/Hit@5를 출력한다.
     */
    @Test
    fun `tuning sweep - hit metrics`() = runBlocking {
        val cases = buildCases()

        val paramSets = listOf(
            ParamSet(name = "gateM=1, chunkK=30", gateSectionsTopM = 1, chunkDenseK = 30, chunkSparseK = 30, rrfK = 60, finalTopK = 5),
            ParamSet(name = "gateM=3, chunkK=50", gateSectionsTopM = 3, chunkDenseK = 50, chunkSparseK = 50, rrfK = 60, finalTopK = 5),
            ParamSet(name = "gateM=5, chunkK=80", gateSectionsTopM = 5, chunkDenseK = 80, chunkSparseK = 80, rrfK = 60, finalTopK = 5),
        )

        val results = paramSets.map { p ->
            val baseProps = DochibotRagProperties(
                gate = DochibotRagProperties.Gate(
                    denseTopK = 20,
                    sparseTopK = 20,
                    sectionsTopM = p.gateSectionsTopM,
                ),
                retrieval = DochibotRagProperties.Retrieval(
                    denseTopK = p.chunkDenseK,
                    sparseTopK = p.chunkSparseK,
                ),
                fusion = DochibotRagProperties.Fusion(rrfK = p.rrfK),
                context = DochibotRagProperties.Context(topN = p.finalTopK),
            )

            val rerankOff = baseProps.copy(rerank = DochibotRagProperties.Rerank(enabled = false))
            val rerankOn = baseProps.copy(
                rerank = DochibotRagProperties.Rerank(
                    enabled = true,
                    candidatesTopK = 100,
                    model = RerankModel.HEURISTIC,
                )
            )

            val baseHybrid = HybridRetrievalService(
                sectionRepo,
                chunkRepo,
                rerankOff,
                RerankerRouter(HeuristicReranker()),
                retrievalMetrics,
                retrievalStructuredLogger,
            )
            val rerankHybrid = HybridRetrievalService(
                sectionRepo,
                chunkRepo,
                rerankOn,
                RerankerRouter(HeuristicReranker()),
                retrievalMetrics,
                retrievalStructuredLogger,
            )

            val baseHit = measureHits(baseHybrid, cases, finalTopK = p.finalTopK)
            val rerankHit = measureHits(rerankHybrid, cases, finalTopK = p.finalTopK)

            p to (baseHit to rerankHit)
        }

        println("\n--- Retrieval tuning benchmark (manual) ---")
        println("cases=${cases.size}")
        results.forEach { (p, pair) ->
            val base = pair.first
            val reranked = pair.second
            val baseH1 = base.hitAt1.toDouble() / base.total.toDouble()
            val baseH5 = base.hitAt5.toDouble() / base.total.toDouble()
            val rerankH1 = reranked.hitAt1.toDouble() / reranked.total.toDouble()
            val rerankH5 = reranked.hitAt5.toDouble() / reranked.total.toDouble()
            println("${p.name} | base Hit@1=${"%.2f".format(baseH1)} (${base.hitAt1}/${base.total}) | base Hit@5=${"%.2f".format(baseH5)} (${base.hitAt5}/${base.total})")
            println("${p.name} | rerank Hit@1=${"%.2f".format(rerankH1)} (${reranked.hitAt1}/${reranked.total}) | rerank Hit@5=${"%.2f".format(rerankH5)} (${reranked.hitAt5}/${reranked.total})")
        }

        // 수동 벤치마크이므로 강한 임계값을 두지 않는다.
        assertTrue(cases.isNotEmpty())
    }

    private data class ParamSet(
        val name: String,
        val gateSectionsTopM: Int,
        val chunkDenseK: Int,
        val chunkSparseK: Int,
        val rrfK: Int,
        val finalTopK: Int,
    )

    private data class Case(
        val token: String,
        val expectedChunkId: UUID,
        val embeddingIndex: Int,
    )

    private data class HitResult(
        val total: Int,
        val hitAt1: Int,
        val hitAt5: Int,
    )

    private suspend fun measureHits(
        hybrid: HybridRetrievalService,
        cases: List<Case>,
        finalTopK: Int,
    ): HitResult {
        var hit1 = 0
        var hit5 = 0

        for (c in cases) {
            val top = hybrid.retrieveTopChunks(
                queryText = c.token,
                queryEmbedding = unitVectorAt(c.embeddingIndex),
                finalTopK = finalTopK,
            )
            if (top.isNotEmpty() && top.first().id == c.expectedChunkId) {
                hit1 += 1
            }
            if (top.take(5).any { it.id == c.expectedChunkId }) {
                hit5 += 1
            }
        }
        return HitResult(total = cases.size, hitAt1 = hit1, hitAt5 = hit5)
    }

    private fun buildCases(): List<Case> {
        // seedDataset()와 동일한 규칙으로 케이스를 생성한다.
        return (0 until 30).map { i ->
            val token = tokenOf(i)
            val expectedChunkId = chunkIdOf(i)
            val embeddingIndex = embeddingIndexOf(i)
            Case(token = token, expectedChunkId = expectedChunkId, embeddingIndex = embeddingIndex)
        }
    }

    private suspend fun seedDataset() {
        val docs = (0 until 3).map { docNo ->
            val docId = UUID.nameUUIDFromBytes("bench-doc-$docNo".toByteArray())
            insertDocument(id = docId, title = "벤치 문서 $docNo", status = "COMPLETED")
            docId
        }

        val sections = mutableMapOf<Pair<Int, Int>, UUID>()
        for (docNo in 0 until 3) {
            for (secNo in 0 until 2) {
                val sectionId = UUID.nameUUIDFromBytes("bench-section-$docNo-$secNo".toByteArray())
                sections[docNo to secNo] = sectionId
                insertSection(
                    sectionId = sectionId,
                    documentId = docs[docNo],
                    heading = "섹션 $secNo",
                    sectionPath = "문서$docNo > 섹션$secNo",
                    sectionText = "${"BENCH_TOKEN"} 문서$docNo 섹션$secNo",
                    embedding = unitVectorAt(secNo),
                )
            }
        }

        for (i in 0 until 30) {
            val docNo = i % 3
            val secNo = i % 2
            val token = tokenOf(i)
            val chunkId = chunkIdOf(i)
            val embeddingIndex = embeddingIndexOf(i)

            insertChunk(
                chunkId = chunkId,
                documentId = docs[docNo],
                sectionId = sections[docNo to secNo]!!,
                chunkIndex = i,
                text = "이 청크는 $token 을 포함한다. 문서=$docNo 섹션=$secNo",
                page = null,
                embedding = unitVectorAt(embeddingIndex),
            )
        }
    }

    private fun tokenOf(i: Int): String = "BENCH_TOKEN_${i.toString().padStart(3, '0')}"

    private fun chunkIdOf(i: Int): UUID = UUID.nameUUIDFromBytes("bench-chunk-$i".toByteArray())

    private fun embeddingIndexOf(i: Int): Int = (10 + i) % 1024

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
        sectionText: String,
        embedding: FloatArray,
    ) {
        databaseClient.sql(
            """
            insert into sections (id, document_id, parent_id, level, heading, section_path, summary, section_text, section_embedding)
            values (:id, :documentId, null, 1, :heading, :sectionPath, null, :sectionText, (:embedding)::vector)
            """.trimIndent()
        )
            .bind("id", sectionId)
            .bind("documentId", documentId)
            .bind("heading", heading)
            .bind("sectionPath", sectionPath)
            .bind("sectionText", sectionText)
            .bind("embedding", toPgVectorLiteral(embedding))
            .fetch()
            .rowsUpdated()
            .awaitSingle()
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
        val spec = databaseClient.sql(
            """
            insert into chunks (id, document_id, section_id, chunk_index, text, page, chunk_embedding)
            values (:id, :documentId, :sectionId, :chunkIndex, :text, :page, (:embedding)::vector)
            """.trimIndent()
        )
            .bind("id", chunkId)
            .bind("documentId", documentId)
            .bind("sectionId", sectionId)
            .bind("chunkIndex", chunkIndex)
            .bind("text", text)
            .bind("embedding", toPgVectorLiteral(embedding))

        (if (page != null) spec.bind("page", page) else spec.bindNull("page", Integer::class.java))
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private fun unitVectorAt(index: Int): FloatArray = RetrievalTestUtils.unitVectorAt(index)

    private fun toPgVectorLiteral(embedding: FloatArray): String = RetrievalTestUtils.toPgVectorLiteral(embedding)
}
