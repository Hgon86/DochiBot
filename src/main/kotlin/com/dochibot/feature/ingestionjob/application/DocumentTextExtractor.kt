package com.dochibot.feature.ingestionjob.application

import com.dochibot.domain.entity.Document
import com.dochibot.domain.enums.DocumentSourceType
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream

/**
 * 문서 소스 타입별 텍스트 추출을 담당한다.
 */
@Service
class DocumentTextExtractor(
    private val markdownSectionParser: MarkdownSectionParser,
) {
    /**
     * 문서 타입에 맞게 텍스트를 추출한다.
     *
     * @param document 문서 메타데이터
     * @param content 원본 바이트
     * @return 페이지 단위 텍스트 목록
     */
    fun extract(document: Document, content: ByteArray): List<ExtractedSection> {
        return when (document.sourceType) {
            DocumentSourceType.TEXT -> extractText(document, content)
            DocumentSourceType.PDF -> extractPdf(document, content)
        }
    }

    private fun extractText(document: Document, content: ByteArray): List<ExtractedSection> {
        val text = String(content, Charsets.UTF_8)
        return markdownSectionParser.parse(documentTitle = document.title, markdown = text)
    }

    private fun extractPdf(document: Document, content: ByteArray): List<ExtractedSection> {
        val pages = mutableListOf<ExtractedSection>()
        var index = 0
        PDDocument.load(ByteArrayInputStream(content)).use { pdf ->
            val permission = pdf.currentAccessPermission
            if (!permission.canExtractContent()) {
                throw IllegalStateException("PDF 콘텐츠 추출 권한이 없습니다.")
            }

            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val totalPages = pdf.numberOfPages
            for (pageIndex in 1..totalPages) {
                stripper.startPage = pageIndex
                stripper.endPage = pageIndex
                val text = stripper.getText(pdf).trim()
                if (text.isNotBlank()) {
                    pages.add(
                        ExtractedSection(
                            index = index++,
                            parentIndex = null,
                            level = 1,
                            heading = "Page $pageIndex",
                            sectionPath = "${document.title} > Page $pageIndex",
                            page = pageIndex,
                            text = text,
                        )
                    )
                }
            }
        }
        return pages
    }
}

/**
 * 추출된 섹션 단위 텍스트.
 *
 * @property index 문서 내 섹션 순번
 * @property parentIndex 부모 섹션 순번
 * @property level heading 레벨
 * @property heading 섹션 제목
 * @property sectionPath 문서 제목을 포함한 섹션 경로
 * @property page PDF 페이지 번호(PDF만 사용)
 * @property text 섹션 본문
 */
data class ExtractedSection(
    val index: Int,
    val parentIndex: Int?,
    val level: Int,
    val heading: String,
    val sectionPath: String,
    val page: Int?,
    val text: String,
)
