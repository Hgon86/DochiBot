package com.dochibot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * DochiBot 애플리케이션 메인 클래스.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class DochiBotApplication

fun main(args: Array<String>) {
    runApplication<DochiBotApplication>(*args)
}
