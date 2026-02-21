package com.dochibot.feature.retrieval.repository

import com.dochibot.feature.retrieval.dto.ChunkCandidate
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * chunks 테이블 기반 Range-limited 검색(dense/sparse)을 수행한다.
 */
@Repository
class ChunkRetrievalRepository(
    private val databaseClient: DatabaseClient,
) {
    /**
     * 문서 상태가 COMPLETED인 전체 청크 중 dense(top-k) 후보를 조회한다.
     *
     * @param queryEmbedding 질의 임베딩
     * @param limit 후보 수
     */
    suspend fun findDenseCandidates(queryEmbedding: FloatArray, limit: Int): List<ChunkCandidate> {
        val sql = """
            select
              c.id as chunk_id,
              c.document_id as document_id,
              d.title as document_title,
              c.section_id as section_id,
              s.section_path as section_path,
              c.text as text,
              c.page as page,
              (c.chunk_embedding <=> (:embedding)::vector) as dist
            from chunks c
            join documents d
              on d.id = c.document_id
             and d.status = 'COMPLETED'
            left join sections s
              on s.id = c.section_id
            where c.section_id is not null
            order by c.chunk_embedding <=> (:embedding)::vector
            limit :limit
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("embedding", toPgVectorLiteral(queryEmbedding))
            .bind("limit", limit)
            .map { row, _ ->
                ChunkCandidate(
                    id = row.get("chunk_id", UUID::class.java)!!,
                    documentId = row.get("document_id", UUID::class.java)!!,
                    documentTitle = row.get("document_title", String::class.java)!!,
                    sectionId = row.get("section_id", UUID::class.java)!!,
                    sectionPath = row.get("section_path", String::class.java),
                    text = row.get("text", String::class.java)!!,
                    page = row.get("page", Integer::class.java)?.toInt(),
                    distance = row.get("dist", java.lang.Double::class.java)?.toDouble(),
                )
            }
            .all()
            .collectList()
            .awaitSingle()
    }

    /**
     * 문서 상태가 COMPLETED인 전체 청크 중 sparse(FTS, top-k) 후보를 조회한다.
     *
     * @param queryText 질의 텍스트
     * @param limit 후보 수
     */
    suspend fun findSparseCandidates(queryText: String, limit: Int): List<ChunkCandidate> {
        val sql = """
            select
              c.id as chunk_id,
              c.document_id as document_id,
              d.title as document_title,
              c.section_id as section_id,
              s.section_path as section_path,
              c.text as text,
              c.page as page,
              ts_rank_cd(c.chunk_tsv, q) as rank
            from chunks c
            join documents d
              on d.id = c.document_id
             and d.status = 'COMPLETED'
            left join sections s
              on s.id = c.section_id
            , websearch_to_tsquery('simple', :query_text) q
            where c.chunk_tsv @@ q
              and c.section_id is not null
            order by rank desc
            limit :limit
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("query_text", queryText)
            .bind("limit", limit)
            .map { row, _ ->
                ChunkCandidate(
                    id = row.get("chunk_id", UUID::class.java)!!,
                    documentId = row.get("document_id", UUID::class.java)!!,
                    documentTitle = row.get("document_title", String::class.java)!!,
                    sectionId = row.get("section_id", UUID::class.java)!!,
                    sectionPath = row.get("section_path", String::class.java),
                    text = row.get("text", String::class.java)!!,
                    page = row.get("page", Integer::class.java)?.toInt(),
                    sparseScore = row.get("rank", java.lang.Double::class.java)?.toDouble(),
                )
            }
            .all()
            .collectList()
            .awaitSingle()
    }

    /**
     * 주어진 섹션 집합에 대해 dense(top-k) 청크 후보를 조회한다.
     *
     * @param sectionIds Gate로 선택된 섹션 ID 목록
     * @param queryEmbedding 질의 임베딩
     * @param limit 후보 수
     */
    suspend fun findDenseCandidatesBySectionIds(
        sectionIds: List<UUID>,
        queryEmbedding: FloatArray,
        limit: Int,
    ): List<ChunkCandidate> {
        if (sectionIds.isEmpty()) return emptyList()

        val inClause = buildInClause(baseName = "section_id", size = sectionIds.size)

        val sql = """
            select
              c.id as chunk_id,
              c.document_id as document_id,
              d.title as document_title,
              c.section_id as section_id,
              s.section_path as section_path,
              c.text as text,
              c.page as page,
              (c.chunk_embedding <=> (:embedding)::vector) as dist
            from chunks c
            join documents d
              on d.id = c.document_id
             and d.status = 'COMPLETED'
            left join sections s
              on s.id = c.section_id
            where c.section_id in $inClause
            order by c.chunk_embedding <=> (:embedding)::vector
            limit :limit
        """.trimIndent()

        var spec = databaseClient.sql(sql)
            .bind("embedding", toPgVectorLiteral(queryEmbedding))
            .bind("limit", limit)
        spec = bindInClauseValues(spec, baseName = "section_id", values = sectionIds)

        return spec
            .map { row, _ ->
                ChunkCandidate(
                    id = row.get("chunk_id", UUID::class.java)!!,
                    documentId = row.get("document_id", UUID::class.java)!!,
                    documentTitle = row.get("document_title", String::class.java)!!,
                    sectionId = row.get("section_id", UUID::class.java)!!,
                    sectionPath = row.get("section_path", String::class.java),
                    text = row.get("text", String::class.java)!!,
                    page = row.get("page", Integer::class.java)?.toInt(),
                    distance = row.get("dist", java.lang.Double::class.java)?.toDouble(),
                )
            }
            .all()
            .collectList()
            .awaitSingle()
    }

    /**
     * 주어진 섹션 집합에 대해 sparse(FTS, top-k) 청크 후보를 조회한다.
     *
     * @param sectionIds Gate로 선택된 섹션 ID 목록
     * @param queryText 질의 텍스트
     * @param limit 후보 수
     */
    suspend fun findSparseCandidatesBySectionIds(
        sectionIds: List<UUID>,
        queryText: String,
        limit: Int,
    ): List<ChunkCandidate> {
        if (sectionIds.isEmpty()) return emptyList()

        val inClause = buildInClause(baseName = "section_id", size = sectionIds.size)

        val sql = """
            select
              c.id as chunk_id,
              c.document_id as document_id,
              d.title as document_title,
              c.section_id as section_id,
              s.section_path as section_path,
              c.text as text,
              c.page as page,
              ts_rank_cd(c.chunk_tsv, q) as rank
            from chunks c
            join documents d
              on d.id = c.document_id
             and d.status = 'COMPLETED'
            left join sections s
              on s.id = c.section_id
            , websearch_to_tsquery('simple', :query_text) q
            where c.chunk_tsv @@ q
              and c.section_id in $inClause
            order by rank desc
            limit :limit
        """.trimIndent()

        var spec = databaseClient.sql(sql)
            .bind("query_text", queryText)
            .bind("limit", limit)
        spec = bindInClauseValues(spec, baseName = "section_id", values = sectionIds)

        return spec
            .map { row, _ ->
                ChunkCandidate(
                    id = row.get("chunk_id", UUID::class.java)!!,
                    documentId = row.get("document_id", UUID::class.java)!!,
                    documentTitle = row.get("document_title", String::class.java)!!,
                    sectionId = row.get("section_id", UUID::class.java)!!,
                    sectionPath = row.get("section_path", String::class.java),
                    text = row.get("text", String::class.java)!!,
                    page = row.get("page", Integer::class.java)?.toInt(),
                    sparseScore = row.get("rank", java.lang.Double::class.java)?.toDouble(),
                )
            }
            .all()
            .collectList()
            .awaitSingle()
    }

    private fun buildInClause(baseName: String, size: Int): String {
        return (0 until size).joinToString(prefix = "(", postfix = ")") { i ->
            ":${baseName}_$i"
        }
    }

    private fun bindInClauseValues(
        spec: DatabaseClient.GenericExecuteSpec,
        baseName: String,
        values: List<UUID>,
    ): DatabaseClient.GenericExecuteSpec {
        var next = spec
        for (i in values.indices) {
            next = next.bind("${baseName}_$i", values[i])
        }
        return next
    }

    private fun toPgVectorLiteral(embedding: FloatArray): String {
        // pgvector 텍스트 입력 포맷: [0.1,0.2,0.3]
        return buildString {
            append('[')
            for (i in embedding.indices) {
                if (i > 0) append(',')
                append(embedding[i])
            }
            append(']')
        }
    }
}
