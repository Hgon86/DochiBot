package com.dochibot.feature.auth.dto

import jakarta.validation.constraints.NotBlank

/**
 * 로그인 요청 DTO.
 *
 * @property username 로그인 아이디
 * @property password 원문 비밀번호
 */
data class LoginRequest(
    @field:NotBlank
    val username: String,
    @field:NotBlank
    val password: String
)
