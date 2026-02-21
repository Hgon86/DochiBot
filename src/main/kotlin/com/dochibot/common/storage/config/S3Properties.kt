package com.dochibot.common.storage.config

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * SeaweedFS S3 gateway 같은 S3 호환 스토리지 연동 설정.
 *
 * 서버 내부 통신용 endpoint(`endpoint`)와 브라우저가 접근할 endpoint(`publicEndpoint`)를 분리한다.
 *
 * @property endpoint 서버에서 접근할 S3 endpoint
 * @property publicEndpoint 클라이언트(브라우저)에서 접근할 S3 endpoint
 * @property bucket 기본 버킷명
 * @property region S3 region
 * @property accessKey 액세스 키
 * @property secretKey 시크릿 키
 * @property pathStyleAccess path-style access 사용 여부
 * @property presignedUrlExpirationSeconds Presigned URL 만료 시간(초)
 */
@Validated
@ConfigurationProperties(prefix = "s3")
data class S3Properties(
    @field:NotBlank
    val endpoint: String = "http://localhost:9000",
    @field:NotBlank
    val publicEndpoint: String = "http://localhost:9000",
    @field:NotBlank
    val bucket: String = "dochi-bot",
    @field:NotBlank
    val region: String = "ap-northeast-2",
    @field:NotBlank
    val accessKey: String = "",
    @field:NotBlank
    val secretKey: String = "",
    val pathStyleAccess: Boolean = false,
    @field:Positive
    val presignedUrlExpirationSeconds: Long = 600,
)
