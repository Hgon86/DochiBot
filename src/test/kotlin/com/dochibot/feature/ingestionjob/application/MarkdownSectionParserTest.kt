package com.dochibot.feature.ingestionjob.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownSectionParserTest {
    private val parser = MarkdownSectionParser()

    @Test
    fun `heading 구조를 section 트리 경로로 변환한다`() {
        val sections = parser.parse(
            documentTitle = "도치봇 리드미",
            markdown = """
                # 개요
                도치봇 소개입니다.

                ## Tech Stack
                - Kotlin
                - Spring Boot

                ### Storage
                SeaweedFS
            """.trimIndent(),
        )

        assertEquals(3, sections.size)
        assertEquals("도치봇 리드미 > 개요", sections[0].sectionPath)
        assertEquals("도치봇 리드미 > 개요 > Tech Stack", sections[1].sectionPath)
        assertEquals(sections[1].index, sections[2].parentIndex)
        assertEquals("도치봇 리드미 > 개요 > Tech Stack > Storage", sections[2].sectionPath)
    }

    @Test
    fun `markdown 장식을 제거하고 본문만 남긴다`() {
        val sections = parser.parse(
            documentTitle = "도치봇 리드미",
            markdown = """
                ## Tech Stack
                - **Backend**: `Kotlin`, [Spring Boot](https://spring.io)
                - Storage: SeaweedFS<br>
                | name | value |
                | --- | --- |
                | cache | Redis |
            """.trimIndent(),
        )

        val text = sections.single().text
        assertTrue(text.contains("Backend: Kotlin, Spring Boot"))
        assertTrue(text.contains("Storage: SeaweedFS"))
        assertTrue(text.contains("cache Redis"))
        assertTrue(!text.contains("**"))
        assertTrue(!text.contains("[Spring Boot]"))
    }
}
