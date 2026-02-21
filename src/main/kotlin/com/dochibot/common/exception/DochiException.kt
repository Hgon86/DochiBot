package com.dochibot.common.exception

/**
 * 도메인/애플리케이션 레벨에서 사용되는 공통 예외.
 */
open class DochiException(
    val errorCode: ErrorCode,
    val detail: String? = null,
    val args: List<Any?> = emptyList(),
    override val cause: Throwable? = null
) : RuntimeException(errorCode.code, cause) {
    constructor(errorCode: ErrorCode, detail: String? = null, cause: Throwable? = null, vararg args: Any?) : this(
        errorCode = errorCode,
        detail = detail,
        args = args.toList(),
        cause = cause
    )
}
