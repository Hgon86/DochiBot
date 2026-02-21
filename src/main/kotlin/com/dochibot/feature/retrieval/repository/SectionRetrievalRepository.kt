package com.dochibot.feature.retrieval.repository

import com.dochibot.feature.retrieval.dto.SectionCandidate
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * sections 테이블 기반 Gate 검색(dense/sparse)을 수행한다.
 */
@Repository
class SectionRetrievalRepository(
    private val databaseClient: DatabaseClient,
) {
    /**
     * 문서 상태가 COMPLETED인 섹션 중 dense(top-k) 후보를 조회한다.
     *
     * @param queryEmbedding 질의 임베딩
     * @param limit 후보 수
     */
    suspend fun findDenseCandidates(queryEmbedding: FloatArray, limit: Int): List<SectionCandidate> {
        val sql = """
            select
              s.id as section_id,
              s.document_id as document_id,
              d.title as document_title,
              s.heading as heading,
              s.section_path as section_path,
              (s.section_embedding <=> (:embedding)::vector) as dist
            from sections s
            join documents d
              on d.id = s.document_id
             and d.status = 'COMPLETED'
            where s.section_embedding is not null
            order by s.section_embedding <=> (:embedding)::vector
            limit :limit
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("embedding", toPgVectorLiteral(queryEmbedding))
            .bind("limit", limit)
            .map { row, _ ->
                SectionCandidate(
                    id = row.get("section_id", UUID::class.java)!!,
                    documentId = row.get("document_id", UUID::class.java)!!,
                    documentTitle = row.get("document_title", String::class.java)!!,
                    heading = row.get("heading", String::class.java) ?: "",
                    sectionPath = row.get("section_path", String::class.java) ?: "",
                    distance = row.get("dist", java.lang.Double::class.java)?.toDouble(),
                )
            }
            .all()
            .collectList()
            .awaitSingle()
    }

    /**
     * 문서 상태가 COMPLETED인 섹션 중 sparse(FTS, top-k) 후보를 조회한다.
     *
     * @param queryText 질의 텍스트
     * @param limit 후보 수
     */
    suspend fun findSparseCandidates(queryText: String, limit: Int): List<SectionCandidate> {
        val sql = """
            select
              s.id as section_id,
              s.document_id as document_id,
              d.title as document_title,
              s.heading as heading,
              s.section_path as section_path,
              ts_rank_cd(s.section_tsv, q) as rank
            from sections s
            join documents d
              on d.id = s.document_id
             and d.status = 'COMPLETED'
            , websearch_to_tsquery('simple', :query_text) q
            where s.section_tsv @@ q
            order by rank desc
            limit :limit
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("query_text", queryText)
            .bind("limit", limit)
            .map { row, _ ->
                SectionCandidate(
                    id = row.get("section_id", UUID::class.java)!!,
                    documentId = row.get("document_id", UUID::class.java)!!,
                    documentTitle = row.get("document_title", String::class.java)!!,
                    heading = row.get("heading", String::class.java) ?: "",
                    sectionPath = row.get("section_path", String::class.java) ?: "",
                    sparseScore = row.get("rank", java.lang.Double::class.java)?.toDouble(),
                )
            }
            .all()
            .collectList()
            .awaitSingle()
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
