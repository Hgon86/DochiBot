package com.dochibot.common.util.text

/**
 * 검색용 메타데이터를 포함한 chunk 텍스트를 인코딩/디코딩한다.
 */
object SearchableChunkTextCodec {
    private const val DOC_PREFIX = "[D] "
    private const val SECTION_PREFIX = "[S] "
    private const val BODY_PREFIX = "[B] "

    /**
     * 문서/섹션 메타데이터를 포함한 검색용 텍스트를 만든다.
     */
    fun encode(documentTitle: String, sectionPath: String, bodyText: String): String {
        val normalizedBody = bodyText.trim()
        return buildString(documentTitle.length + sectionPath.length + normalizedBody.length + 32) {
            append(DOC_PREFIX)
            append(documentTitle.trim())
            append('\n')
            append(SECTION_PREFIX)
            append(sectionPath.trim())
            append('\n')
            append(BODY_PREFIX)
            append(normalizedBody)
        }
    }

    /**
     * 검색용 텍스트에서 메타데이터를 제외한 본문만 추출한다.
     */
    fun decodeBody(text: String): String {
        val bodyIndex = text.indexOf(BODY_PREFIX)
        if (bodyIndex < 0) {
            return text.trim()
        }

        return text.substring(bodyIndex + BODY_PREFIX.length).trim()
    }

    /**
     * 검색용 메타데이터 prefix 길이를 계산한다.
     */
    fun prefixLength(documentTitle: String, sectionPath: String): Int {
        return encode(documentTitle = documentTitle, sectionPath = sectionPath, bodyText = "").length
    }
}
