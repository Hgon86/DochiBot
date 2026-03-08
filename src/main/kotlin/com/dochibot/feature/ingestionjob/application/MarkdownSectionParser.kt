package com.dochibot.feature.ingestionjob.application

import org.springframework.stereotype.Component

/**
 * Markdown 문서를 heading 단위 섹션으로 분해하고 검색 친화적인 plain text로 정규화한다.
 */
@Component
class MarkdownSectionParser {
    private companion object {
        val headingRegex = Regex("""^(#{1,6})\s+(.*\S)\s*$""")
        val codeFenceRegex = Regex("""^```""")
        val unorderedListRegex = Regex("""^\s*[-*+]\s+""")
        val orderedListRegex = Regex("""^\s*\d+[.)]\s+""")
        val blockQuoteRegex = Regex("""^\s*>+\s*""")
        val tableRuleRegex = Regex("""^\s*\|?\s*[:\-]+(?:\s*\|\s*[:\-]+)+\s*\|?\s*$""")
        val linkRegex = Regex("""\[([^\]]+)]\(([^)]+)\)""")
        val imageRegex = Regex("""!\[([^\]]*)]\(([^)]+)\)""")
        val inlineCodeRegex = Regex("""`([^`]+)`""")
        val htmlTagRegex = Regex("""<[^>]+>""")
        val emphasisRegex = Regex("""[*_~]+""")
        val whitespaceRegex = Regex("""\s+""")
    }

    fun parse(documentTitle: String, markdown: String): List<ExtractedSection> {
        val lines = markdown
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()

        val sections = mutableListOf<ExtractedSection>()
        val stack = mutableListOf<HeadingRef>()
        val introLines = mutableListOf<String>()
        var current: SectionDraft? = null
        var nextIndex = 0
        var insideCodeFence = false

        fun finalizeCurrent() {
            val draft = current ?: return
            val normalized = normalizeSectionText(draft.lines)
            val shouldKeep = normalized.isNotBlank() || draft.sectionPath != documentTitle
            if (!shouldKeep) {
                current = null
                return
            }
            sections += ExtractedSection(
                index = draft.index,
                parentIndex = draft.parentIndex,
                level = draft.level,
                heading = draft.heading,
                sectionPath = draft.sectionPath,
                page = null,
                text = normalized,
            )
            current = null
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (codeFenceRegex.containsMatchIn(trimmed)) {
                insideCodeFence = !insideCodeFence
                continue
            }

            if (!insideCodeFence) {
                val headingMatch = headingRegex.matchEntire(trimmed)
                if (headingMatch != null) {
                    finalizeCurrent()

                    val level = headingMatch.groupValues[1].length
                    val heading = cleanInlineMarkdown(headingMatch.groupValues[2])
                    if (heading.isBlank()) {
                        continue
                    }

                    if (sections.isEmpty() && introLines.isNotEmpty()) {
                        val introText = normalizeSectionText(introLines)
                        if (introText.isNotBlank()) {
                            sections += ExtractedSection(
                                index = nextIndex++,
                                parentIndex = null,
                                level = 1,
                                heading = documentTitle,
                                sectionPath = documentTitle,
                                page = null,
                                text = introText,
                            )
                        }
                        introLines.clear()
                    }

                    while (stack.isNotEmpty() && stack.last().level >= level) {
                        stack.removeLast()
                    }

                    val parentIndex = stack.lastOrNull()?.index
                    val headingChain = buildList {
                        add(documentTitle)
                        addAll(stack.map { it.heading })
                        add(heading)
                    }
                    current = SectionDraft(
                        index = nextIndex++,
                        parentIndex = parentIndex,
                        level = level,
                        heading = heading,
                        sectionPath = headingChain.joinToString(" > "),
                    )
                    stack += HeadingRef(index = current!!.index, level = level, heading = heading)
                    continue
                }
            }

            val cleanedLine = cleanContentLine(line, insideCodeFence)
            if (cleanedLine.isBlank()) {
                continue
            }

            if (current == null) {
                introLines += cleanedLine
            } else {
                current!!.lines += cleanedLine
            }
        }

        finalizeCurrent()

        if (sections.isEmpty()) {
            val normalized = normalizeSectionText(introLines)
            if (normalized.isNotBlank()) {
                sections += ExtractedSection(
                    index = 0,
                    parentIndex = null,
                    level = 1,
                    heading = documentTitle,
                    sectionPath = documentTitle,
                    page = null,
                    text = normalized,
                )
            }
        }

        return sections
    }

    private fun cleanContentLine(line: String, insideCodeFence: Boolean): String {
        if (insideCodeFence) {
            return whitespaceRegex.replace(line.trim(), " ")
        }

        val trimmed = line.trim()
        if (trimmed.isBlank()) return ""
        if (tableRuleRegex.matches(trimmed)) return ""
        if (trimmed == "---" || trimmed == "***") return ""

        var text = trimmed
        text = blockQuoteRegex.replace(text, "")
        text = unorderedListRegex.replace(text, "")
        text = orderedListRegex.replace(text, "")
        text = cleanInlineMarkdown(text)
        return whitespaceRegex.replace(text, " ").trim()
    }

    private fun cleanInlineMarkdown(text: String): String {
        var normalized = text
        normalized = imageRegex.replace(normalized) { it.groupValues[1] }
        normalized = linkRegex.replace(normalized) { it.groupValues[1] }
        normalized = inlineCodeRegex.replace(normalized) { it.groupValues[1] }
        normalized = htmlTagRegex.replace(normalized, " ")
        normalized = normalized.replace('|', ' ')
        normalized = emphasisRegex.replace(normalized, "")
        return whitespaceRegex.replace(normalized, " ").trim()
    }

    private fun normalizeSectionText(lines: List<String>): String {
        return lines
            .joinToString(" ")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    private data class HeadingRef(
        val index: Int,
        val level: Int,
        val heading: String,
    )

    private data class SectionDraft(
        val index: Int,
        val parentIndex: Int?,
        val level: Int,
        val heading: String,
        val sectionPath: String,
        val lines: MutableList<String> = mutableListOf(),
    )
}
