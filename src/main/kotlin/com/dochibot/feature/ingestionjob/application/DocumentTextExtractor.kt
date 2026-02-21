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
class DocumentTextExtractor {
    /**
     * 문서 타입에 맞게 텍스트를 추출한다.
     *
     * @param document 문서 메타데이터
     * @param content 원본 바이트
     * @return 페이지 단위 텍스트 목록
     */
    fun extract(document: Document, content: ByteArray): List<ExtractedPage> {
        return when (document.sourceType) {
            DocumentSourceType.TEXT -> extractText(content)
            DocumentSourceType.PDF -> extractPdf(content)
        }
    }

    private fun extractText(content: ByteArray): List<ExtractedPage> {
        val text = String(content, Charsets.UTF_8)
        return listOf(ExtractedPage(page = null, text = text))
    }

    private fun extractPdf(content: ByteArray): List<ExtractedPage> {
        val pages = mutableListOf<ExtractedPage>()
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
                    pages.add(ExtractedPage(page = pageIndex, text = text))
                }
            }
        }
        return pages
    }
}

/**
 * 추출된 페이지 단위 텍스트.
 *
 * @property page 페이지 번호(PDF만 사용, 1부터 시작)
 * @property text 페이지 텍스트
 */
data class ExtractedPage(
    val page: Int?,
    val text: String,
)
