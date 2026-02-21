package com.dochibot.common.storage.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

/**
 * S3Client/S3Presigner Bean 구성.
 *
 * - S3Client: 서버 내부 통신용 endpoint를 사용
 * - S3Presigner: 클라이언트 접근용 publicEndpoint 기준으로 presigned URL 생성
 */
@Configuration
class S3Config(
    private val s3Properties: S3Properties,
) {
    @Bean
    fun s3CredentialsProvider(): AwsCredentialsProvider {
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(s3Properties.accessKey, s3Properties.secretKey),
        )
    }

    @Bean
    fun s3Region(): Region = Region.of(s3Properties.region)

    @Bean
    fun s3ServiceConfiguration(): S3Configuration {
        return S3Configuration.builder()
            .pathStyleAccessEnabled(s3Properties.pathStyleAccess)
            .build()
    }

    @Bean(destroyMethod = "close")
    fun s3Client(
        credentialsProvider: AwsCredentialsProvider,
        region: Region,
        s3Configuration: S3Configuration,
    ): S3Client {
        return S3Client.builder()
            .credentialsProvider(credentialsProvider)
            .region(region)
            .serviceConfiguration(s3Configuration)
            .endpointOverride(URI.create(s3Properties.endpoint))
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build()
    }

    @Bean(destroyMethod = "close")
    fun s3AsyncClient(
        credentialsProvider: AwsCredentialsProvider,
        region: Region,
        s3Configuration: S3Configuration,
    ): S3AsyncClient {
        return S3AsyncClient.builder()
            .credentialsProvider(credentialsProvider)
            .region(region)
            .serviceConfiguration(s3Configuration)
            .endpointOverride(URI.create(s3Properties.endpoint))
            .httpClientBuilder(NettyNioAsyncHttpClient.builder())
            .build()
    }

    @Bean(destroyMethod = "close")
    fun s3Presigner(
        credentialsProvider: AwsCredentialsProvider,
        region: Region,
        s3Configuration: S3Configuration,
    ): S3Presigner {
        return S3Presigner.builder()
            .credentialsProvider(credentialsProvider)
            .region(region)
            .endpointOverride(URI.create(s3Properties.publicEndpoint))
            .serviceConfiguration(s3Configuration)
            .build()
    }
}
