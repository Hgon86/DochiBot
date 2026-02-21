package com.dochibot.common.exception

import com.dochibot.common.config.RequestIdWebFilter
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException

/**
 * 전역 예외를 API 에러 응답으로 변환한다.
 */
@RestControllerAdvice
class GlobalExceptionHandler(
    private val errorI18nSupport: ErrorI18nSupport,
    private val environment: Environment,
) {
    private val log = KotlinLogging.logger {}

    /**
     * 도메인 예외를 표준 에러 응답으로 변환한다.
     *
     * @param e 도메인 예외
     * @param exchange 현재 요청/응답 교환 컨텍스트
     * @return 표준 에러 응답
     */
    @ExceptionHandler(DochiException::class)
    fun handleDochiException(e: DochiException, exchange: ServerWebExchange): ResponseEntity<ApiErrorResponse> {
        val traceId = requestId(exchange)
        val status = e.errorCode.status
        if (status.is5xxServerError) {
            log.error(e) {
                "Handled DochiException(5xx): code=${e.errorCode.code}, detail=${e.detail}, traceId=$traceId"
            }
        } else {
            if (shouldExposeDetail()) {
                log.warn(e) {
                    "Handled DochiException: code=${e.errorCode.code}, detail=${e.detail}, traceId=$traceId"
                }
            } else {
                log.warn {
                    "Handled DochiException: code=${e.errorCode.code}, detail=${e.detail}, traceId=$traceId"
                }
            }
        }
        val code = e.errorCode.code
        val detail = if (shouldExposeDetail()) e.detail else null
        return ResponseEntity.status(status).body(
            ApiErrorResponse(
                status = status.value(),
                code = code,
                message = errorI18nSupport.defaultEnglishMessage(code),
                i18nKey = errorI18nSupport.i18nKey(e.errorCode),
                i18nArgs = errorI18nSupport.i18nArgs(*e.args.toTypedArray()),
                detail = detail,
                traceId = traceId
            )
        )
    }

    /**
     * Bean Validation 실패를 필드 에러 목록과 함께 반환한다.
     *
     * @param e 검증 예외
     * @param exchange 현재 요청/응답 교환 컨텍스트
     * @return 표준 에러 응답
     */
    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidation(e: WebExchangeBindException, exchange: ServerWebExchange): ResponseEntity<ApiErrorResponse> {
        log.warn { "Validation failed: traceId=${requestId(exchange)}" }
        val code = CommonErrorCode.VALIDATION_ERROR.code
        val status = CommonErrorCode.VALIDATION_ERROR.status
        val errors = e.bindingResult.fieldErrors.map {
            FieldError(
                field = it.field,
                value = it.rejectedValue?.toString(),
                reason = it.defaultMessage ?: "invalid"
            )
        }

        return ResponseEntity.status(status).body(
            ApiErrorResponse(
                status = status.value(),
                code = code,
                message = errorI18nSupport.defaultEnglishMessage(code),
                i18nKey = errorI18nSupport.i18nKey(code),
                errors = errors,
                traceId = requestId(exchange)
            )
        )
    }

    /**
     * 요청 바디/쿼리 파싱 실패를 검증 오류로 반환한다.
     *
     * @param e 입력 예외
     * @param exchange 현재 요청/응답 교환 컨텍스트
     * @return 표준 에러 응답
     */
    @ExceptionHandler(ServerWebInputException::class)
    fun handleInput(e: ServerWebInputException, exchange: ServerWebExchange): ResponseEntity<ApiErrorResponse> {
        log.warn { "Input parsing failed: reason=${e.reason}, traceId=${requestId(exchange)}" }
        val code = CommonErrorCode.VALIDATION_ERROR.code
        val status = CommonErrorCode.VALIDATION_ERROR.status
        return ResponseEntity.status(status).body(
            ApiErrorResponse(
                status = status.value(),
                code = code,
                message = errorI18nSupport.defaultEnglishMessage(code),
                i18nKey = errorI18nSupport.i18nKey(code),
                detail = if (shouldExposeDetail()) e.reason else null,
                traceId = requestId(exchange)
            )
        )
    }

    /**
     * 프레임워크 레벨의 상태 예외를 공통 에러 코드로 매핑한다.
     *
     * @param e 상태 예외
     * @param exchange 현재 요청/응답 교환 컨텍스트
     * @return 표준 에러 응답
     */
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(
        e: ResponseStatusException,
        exchange: ServerWebExchange
    ): ResponseEntity<ApiErrorResponse> {
        log.warn {
            "ResponseStatusException: status=${e.statusCode.value()}, reason=${e.reason}, traceId=${requestId(exchange)}"
        }
        val status = e.statusCode
        val code = when (status) {
            HttpStatus.NOT_FOUND -> CommonErrorCode.NOT_FOUND.code
            HttpStatus.CONFLICT -> CommonErrorCode.CONFLICT.code
            HttpStatus.FORBIDDEN -> CommonErrorCode.AUTH_FORBIDDEN.code
            HttpStatus.UNAUTHORIZED -> CommonErrorCode.AUTH_REQUIRED.code
            else -> CommonErrorCode.INTERNAL_ERROR.code
        }

        return ResponseEntity.status(status).body(
            ApiErrorResponse(
                status = status.value(),
                code = code,
                message = errorI18nSupport.defaultEnglishMessage(code),
                i18nKey = errorI18nSupport.i18nKey(code),
                detail = null,
                traceId = requestId(exchange)
            )
        )
    }

    /**
     * 처리되지 않은 예외를 내부 오류로 변환한다.
     *
     * @param e 예외
     * @param exchange 현재 요청/응답 교환 컨텍스트
     * @return 표준 에러 응답
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception, exchange: ServerWebExchange): ResponseEntity<ApiErrorResponse> {
        log.error(e) { "Unhandled exception: traceId=${requestId(exchange)}" }
        val code = CommonErrorCode.INTERNAL_ERROR.code
        val status = CommonErrorCode.INTERNAL_ERROR.status
        return ResponseEntity.status(status).body(
            ApiErrorResponse(
                status = status.value(),
                code = code,
                message = errorI18nSupport.defaultEnglishMessage(code),
                i18nKey = errorI18nSupport.i18nKey(code),
                detail = if (shouldExposeDetail()) e.message else null,
                traceId = requestId(exchange)
            )
        )
    }

    private fun shouldExposeDetail(): Boolean {
        val profiles = environment.activeProfiles.toSet()
        return profiles.contains("local") || profiles.contains("docker")
    }

    /**
     * 필터에서 생성/주입한 Request ID를 추출한다.
     *
     * @param exchange 현재 요청/응답 교환 컨텍스트
     * @return Request ID (없으면 null)
     */
    private fun requestId(exchange: ServerWebExchange): String? {
        return exchange.getAttribute<String>(RequestIdWebFilter.ATTR_REQUEST_ID)
            ?: exchange.request.headers.getFirst(RequestIdWebFilter.HEADER_REQUEST_ID)
    }
}
