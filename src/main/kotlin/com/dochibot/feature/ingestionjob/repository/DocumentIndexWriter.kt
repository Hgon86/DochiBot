package com.dochibot.feature.ingestionjob.repository

import com.dochibot.common.util.id.Uuid7Generator
import com.dochibot.feature.ingestionjob.application.TextChunk
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * 문서 인덱스(sections/chunks) 쓰기 작업을 담당한다.
 */
@Repository
class DocumentIndexWriter(
    private val databaseClient: DatabaseClient,
) {
    /**
     * 문서에 연결된 sections/chunks를 제거한다(재인덱싱용).
     *
     * @param documentId 문서 ID
     */
    suspend fun deleteByDocumentId(documentId: UUID) {
        // chunks -> sections 순서로 삭제해야 orphan이 남지 않는다.
        databaseClient.sql("delete from chunks where document_id = :documentId")
            .bind("documentId", documentId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        databaseClient.sql("delete from sections where document_id = :documentId")
            .bind("documentId", documentId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    suspend fun insertSections(documentId: UUID, sections: List<IndexedSectionInput>): Map<Int, UUID> {
        val sql = """
            insert into sections (id, document_id, parent_id, level, heading, section_path, summary, section_text)
            values (:id, :documentId, :parentId, :level, :heading, :sectionPath, null, :sectionText)
        """.trimIndent()

        val idsByIndex = linkedMapOf<Int, UUID>()
        for (section in sections) {
            val sectionId = Uuid7Generator.create()
            var spec = databaseClient.sql(sql)
                .bind("id", sectionId)
                .bind("documentId", documentId)
                .bind("level", section.level)
                .bind("heading", section.heading)
                .bind("sectionPath", section.sectionPath)

            spec = if (section.parentIndex != null) {
                spec.bind("parentId", idsByIndex[section.parentIndex] ?: error("parent section not found: ${section.parentIndex}"))
            } else {
                spec.bindNull("parentId", UUID::class.java)
            }

            spec = if (section.sectionText.isBlank()) {
                spec.bindNull("sectionText", String::class.java)
            } else {
                spec.bind("sectionText", section.sectionText)
            }

            spec.fetch()
                .rowsUpdated()
                .awaitSingle()

            idsByIndex[section.index] = sectionId
        }

        return idsByIndex
    }

    /**
     * 섹션 임베딩을 갱신한다.
     *
     * @param sectionId 섹션 ID
     * @param embedding 임베딩
     */
    suspend fun updateSectionEmbedding(sectionId: UUID, embedding: FloatArray) {
        databaseClient.sql(
            """
            update sections
            set section_embedding = (:embedding)::vector
            where id = :sectionId
            """.trimIndent()
        )
            .bind("sectionId", sectionId)
            .bind("embedding", toPgVectorLiteral(embedding))
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    /**
     * 청크를 저장한다.
     *
     * @param documentId 문서 ID
     * @param sectionId 소속 섹션 ID
     * @param chunks 청크 목록
     * @param embeddings chunks와 동일 순서의 임베딩 목록
     */
    suspend fun insertChunks(
        documentId: UUID,
        sectionIdsByIndex: Map<Int, UUID>,
        chunks: List<TextChunk>,
        embeddings: List<FloatArray>,
    ) {
        require(chunks.size == embeddings.size) {
            "chunks.size and embeddings.size must match"
        }

        val sql = """
            insert into chunks (
              id, document_id, section_id, chunk_index, text, page, start_offset, end_offset, chunk_embedding
            )
            values (
              :id, :documentId, :sectionId, :chunkIndex, :text, :page, null, null, (:embedding)::vector
            )
        """.trimIndent()

        for (i in chunks.indices) {
            val chunk = chunks[i]
            val embedding = embeddings[i]
            val chunkId = Uuid7Generator.create()

            var spec = databaseClient.sql(sql)
                .bind("id", chunkId)
                .bind("documentId", documentId)
                .bind("sectionId", sectionIdsByIndex[chunk.sectionIndex] ?: error("sectionId not found: ${chunk.sectionIndex}"))
                .bind("chunkIndex", chunk.index)
                .bind("text", chunk.text)

            spec = if (chunk.page != null) {
                spec.bind("page", chunk.page)
            } else {
                spec.bindNull("page", Integer::class.java)
            }

            spec.bind("embedding", toPgVectorLiteral(embedding))
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
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

data class IndexedSectionInput(
    val index: Int,
    val parentIndex: Int?,
    val level: Int,
    val heading: String,
    val sectionPath: String,
    val sectionText: String,
)
