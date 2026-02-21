package com.dochibot.common.exception

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Locale

/**
 * 에러 i18n 관련 CoC(Convention over Configuration) 지원 유틸.
 *
 * - i18nKey는 별도 매핑/하드코딩 없이 "규칙 기반"으로 자동 파생한다.
 * - 프론트가 i18n 최종 책임을 가지므로, 백엔드는 i18nKey + 영문 fallback 메시지를 함께 내려준다.
 */
@Component
class ErrorI18nSupport(
    // 환경별로 오버라이드 가능 (예: dochibot, dochibot.service 등)
    @Value("\${app.error.namespace:\${spring.application.name:dochibot}}")
    namespace: String
) {
    private val namespace: String = normalizeNamespace(namespace)

    /**
     * ErrorCode 기반 i18nKey 생성.
     * 예: namespace=dochibot, AUTH_INVALID_TOKEN -> error.dochibot.auth.invalid.token
     */
    fun i18nKey(errorCode: ErrorCode): String = i18nKey(errorCode.code)

    /**
     * String code 기반 i18nKey 생성(검증 에러 등 ErrorCode 밖의 케이스용).
     */
    fun i18nKey(code: String): String = "error.$namespace.${normalizeCode(code)}"

    /**
     * 메시지 번들이 아직 없거나(또는 키가 누락된 경우) 사용하기 위한 영문 fallback 메시지.
     * 예: FILE_NOT_FOUND -> "File not found"
     */
    fun defaultEnglishMessage(code: String?): String {
        if (code.isNullOrBlank()) {
            return "Unknown error"
        }

        val tokens = code.trim().split(Regex("[_\\s.]+"))
        val sentence = tokens
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                if (token.any { it.isDigit() }) {
                    token.uppercase(Locale.ROOT)
                } else {
                    token.lowercase(Locale.ROOT)
                }
            }
            .trim()

        if (sentence.isBlank()) {
            return "Unknown error"
        }

        return sentence.replaceFirstChar { c ->
            if (c.isLowerCase()) c.titlecase(Locale.ROOT) else c.toString()
        }
    }

    fun defaultEnglishMessage(errorCode: ErrorCode): String = defaultEnglishMessage(errorCode.code)

    /**
     * 프론트 i18n 치환을 위한 positional args(선택).
     * - 민감 정보가 들어갈 수 있으므로, 길이/개수 제한을 둔다.
     */
    fun i18nArgs(vararg args: Any?): List<String> {
        if (args.isEmpty()) return emptyList()

        return args.asSequence()
            .filterNotNull()
            .map { it.toString() }
            .map { truncate(it) }
            .take(5)
            .toList()
    }

    private fun truncate(value: String): String =
        if (value.length > 200) value.substring(0, 200) else value

    private fun normalizeCode(code: String): String =
        code.trim()
            .lowercase(Locale.ROOT)
            .replace("_", ".")
            .replace("-", ".")

    private fun normalizeNamespace(raw: String?): String {
        if (raw.isNullOrBlank()) return "dochibot"
        return raw.trim()
            .lowercase(Locale.ROOT)
            .replace("_", ".")
            .replace("-", ".")
    }
}
