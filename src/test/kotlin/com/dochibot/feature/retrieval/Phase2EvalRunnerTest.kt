package com.dochibot.feature.retrieval

import com.dochibot.feature.retrieval.application.HybridRetrievalService
import com.dochibot.feature.retrieval.eval.Phase2EvalScorer
import com.dochibot.feature.retrieval.eval.Phase2EvalSet
import com.dochibot.feature.retrieval.eval.Phase2EvalValidator
import com.dochibot.feature.retrieval.mock.MockDocumentStore
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.boot.test.context.SpringBootTest
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * Phase 2 평가셋을 기반으로 리트리벌 품질을 수동 측정하는 러너.
 *
 * 실행 예:
 * - `./gradlew.bat test -Ddochibot.eval=true -Ddochibot.eval.path=C:/path/phase2_eval.json --tests com.dochibot.feature.retrieval.Phase2EvalRunnerTest`
 */
@EnabledIfSystemProperty(named = "dochibot.eval", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class Phase2EvalRunnerTest(
    private val objectMapper: ObjectMapper,
    private val embeddingModel: EmbeddingModel,
    private val hybridRetrievalService: HybridRetrievalService,
) {
    private val log = KotlinLogging.logger {}

    /**
     * 평가셋 기반 회귀 측정을 실행한다.
     */
    @Test
    fun `Phase 2 eval set을 실행한다`() {
        runBlocking {
            val useMockStore = System.getProperty("dochibot.eval.mock.enabled")
                ?.trim()
                ?.equals("true", ignoreCase = true)
                ?: false
            val evalPath = System.getProperty("dochibot.eval.path")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val mockStorePath = System.getProperty("dochibot.eval.mock.path")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val reportPath = System.getProperty("dochibot.eval.report.path")
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            val evalJson = if (evalPath != null) {
                Files.readString(Path.of(evalPath))
            } else {
                javaClass.classLoader
                    .getResourceAsStream("eval/phase2_eval_sample.json")
                    ?.bufferedReader()
                    ?.readText()
                    ?: error("Resource not found: eval/phase2_eval_sample.json")
            }

            val evalSet: Phase2EvalSet = objectMapper.readValue(evalJson, Phase2EvalSet::class.java)
            val schemaErrors = Phase2EvalValidator.validate(evalSet)
            require(schemaErrors.isEmpty()) {
                "Invalid eval set schema:\n- ${schemaErrors.joinToString("\n- ")}"
            }

            val mockStore = if (useMockStore) {
                loadMockStore(mockStorePath)
            } else {
                null
            }

            val total = evalSet.items.size
            var hit1 = 0
            var hit3 = 0
            var hit5 = 0
            var mrrSum = 0.0
            val failCases = mutableListOf<Map<String, Any?>>()

            log.info { "Phase2 eval started: total=$total, source=${evalPath ?: "classpath:eval/phase2_eval_sample.json"}" }

            for ((idx, item) in evalSet.items.withIndex()) {
                val query = item.query.trim()
                val top5 = if (mockStore != null) {
                    mockStore.retrieveTopChunks(query = query, topK = 5)
                } else {
                    val queryEmbedding = embeddingModel.embed(listOf(query)).first()
                    hybridRetrievalService.retrieveTopChunks(
                        queryText = query,
                        queryEmbedding = queryEmbedding,
                        finalTopK = 5,
                    )
                }

                val ranked = Phase2EvalScorer.score(item, top5)
                if (ranked.hit1) hit1++
                if (ranked.hit3) hit3++
                if (ranked.hit5) hit5++
                mrrSum += ranked.reciprocalRank

                val top1 = top5.firstOrNull()
                log.info {
                    "[$idx/${total - 1}] id=${item.id} rank=${ranked.rank} hit1=${ranked.hit1} hit3=${ranked.hit3} hit5=${ranked.hit5} " +
                        "top1Title=${top1?.documentTitle} top1Score=${top1?.finalScore}"
                }

                if (!ranked.hit5) {
                    failCases += mapOf(
                        "id" to item.id,
                        "query" to item.query,
                        "expected" to item.expected,
                        "top1Title" to top1?.documentTitle,
                        "top1Score" to top1?.finalScore,
                    )
                }
            }

            val hit1Rate = hit1.toDouble() / total.toDouble()
            val hit3Rate = hit3.toDouble() / total.toDouble()
            val hit5Rate = hit5.toDouble() / total.toDouble()
            val mrr = mrrSum / total.toDouble()

            val report = mapOf(
                "total" to total,
                "hitAt1" to hit1Rate,
                "hitAt3" to hit3Rate,
                "hitAt5" to hit5Rate,
                "mrr" to mrr,
                "failCount" to failCases.size,
                "mockStoreEnabled" to useMockStore,
                "failCases" to failCases,
            )

            if (reportPath != null) {
                val output = Path.of(reportPath)
                output.parent?.let { Files.createDirectories(it) }
                if (output.toString().lowercase(Locale.ROOT).endsWith(".html")) {
                    val reportHtml = toHtmlReport(
                        total = total,
                        hit1 = hit1,
                        hit3 = hit3,
                        hit5 = hit5,
                        hit1Rate = hit1Rate,
                        hit3Rate = hit3Rate,
                        hit5Rate = hit5Rate,
                        mrr = mrr,
                        failCases = failCases,
                        mockStoreEnabled = useMockStore,
                    )
                    Files.writeString(output, reportHtml)
                } else {
                    val reportJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report)
                    Files.writeString(output, reportJson)
                }
                log.info { "Phase2 eval report written: path=$reportPath" }
            }

            log.info {
                "Phase2 eval finished: Hit@1=$hit1/$total ($hit1Rate), Hit@3=$hit3/$total ($hit3Rate), " +
                    "Hit@5=$hit5/$total ($hit5Rate), MRR=$mrr"
            }
        }
    }

    /**
     * Mock 문서 저장소를 로딩한다.
     *
     * @param mockStorePath mock 데이터셋 파일 경로(없으면 classpath 샘플 사용)
     * @return 로딩된 MockDocumentStore
     */
    private fun loadMockStore(mockStorePath: String?): MockDocumentStore {
        val mockJson = if (mockStorePath != null) {
            Files.readString(Path.of(mockStorePath))
        } else {
            javaClass.classLoader
                .getResourceAsStream("eval/phase2_mock_documents_sample.json")
                ?.bufferedReader()
                ?.readText()
                ?: error("Resource not found: eval/phase2_mock_documents_sample.json")
        }
        return MockDocumentStore.fromJson(mockJson, objectMapper)
    }

    /**
     * 평가 결과를 HTML 문자열로 렌더링한다.
     *
     * @param total 총 평가 건수
     * @param hit1 Hit@1 적중 건수
     * @param hit3 Hit@3 적중 건수
     * @param hit5 Hit@5 적중 건수
     * @param hit1Rate Hit@1 비율
     * @param hit3Rate Hit@3 비율
     * @param hit5Rate Hit@5 비율
     * @param mrr MRR
     * @param failCases 실패 케이스 목록
     * @param mockStoreEnabled mock 저장소 모드 활성 여부
     * @return HTML 보고서
     */
    private fun toHtmlReport(
        total: Int,
        hit1: Int,
        hit3: Int,
        hit5: Int,
        hit1Rate: Double,
        hit3Rate: Double,
        hit5Rate: Double,
        mrr: Double,
        failCases: List<Map<String, Any?>>,
        mockStoreEnabled: Boolean,
    ): String {
        val rows = failCases.joinToString(separator = "") { fail ->
            val id = escapeHtml(fail["id"]?.toString().orEmpty())
            val query = escapeHtml(fail["query"]?.toString().orEmpty())
            val top1Title = escapeHtml(fail["top1Title"]?.toString().orEmpty())
            val top1Score = escapeHtml(fail["top1Score"]?.toString().orEmpty())
            "<tr><td>$id</td><td>$query</td><td>$top1Title</td><td>$top1Score</td></tr>"
        }
        return """
            <!doctype html>
            <html lang="ko">
            <head>
              <meta charset="utf-8" />
              <title>Phase2 Eval Report</title>
              <style>
                body { font-family: sans-serif; margin: 24px; }
                table { border-collapse: collapse; width: 100%; margin-top: 12px; }
                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                th { background: #f5f5f5; }
                .metric { margin: 4px 0; }
              </style>
            </head>
            <body>
              <h1>Phase2 Eval Report</h1>
              <p class="metric">Mock Store: $mockStoreEnabled</p>
              <p class="metric">Total: $total</p>
              <p class="metric">Hit@1: $hit1/$total (${hit1Rate.format(4)})</p>
              <p class="metric">Hit@3: $hit3/$total (${hit3Rate.format(4)})</p>
              <p class="metric">Hit@5: $hit5/$total (${hit5Rate.format(4)})</p>
              <p class="metric">MRR: ${mrr.format(4)}</p>
              <h2>Fail Cases (${failCases.size})</h2>
              <table>
                <thead>
                  <tr><th>id</th><th>query</th><th>top1Title</th><th>top1Score</th></tr>
                </thead>
                <tbody>
                  $rows
                </tbody>
              </table>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * 숫자 값을 지정한 자릿수 문자열로 포맷한다.
     *
     * @param scale 소수점 자릿수
     * @return 고정 소수점 문자열
     */
    private fun Double.format(scale: Int): String = "%1$.${scale}f".format(Locale.ROOT, this)

    /**
     * HTML 안전 문자열로 이스케이프한다.
     *
     * @param raw 원본 문자열
     * @return HTML 이스케이프 문자열
     */
    private fun escapeHtml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
