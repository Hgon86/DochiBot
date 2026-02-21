package com.dochibot.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator

/**
 * R2DBC Repository/Auditing 활성화 설정.
 */
@Configuration
@EnableR2dbcRepositories(basePackages = ["com.dochibot.domain.repository"])
@EnableR2dbcAuditing
class R2dbcConfig

/**
 * 코루틴 기반 R2DBC 트랜잭션을 프로그램 방식으로 사용하기 위한 Operator Bean.
 */
@Configuration
class R2dbcTransactionOperatorConfig {
    @Bean
    fun transactionalOperator(reactiveTransactionManager: ReactiveTransactionManager): TransactionalOperator {
        return TransactionalOperator.create(reactiveTransactionManager)
    }
}
