package com.dochibot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DochiBotApplication

fun main(args: Array<String>) {
    runApplication<DochiBotApplication>(*args)
}
