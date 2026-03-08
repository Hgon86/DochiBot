package com.dochibot.common.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.memory.MessageWindowChatMemory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring AI(ChatClient/메모리) 구성.
 */
@Configuration
class SpringAiConfig(
    private val dochibotAiProperties: DochibotAiProperties,
) {
    /**
     * 대화 윈도우 기반 메모리.
     */
    @Bean
    fun chatMemory(): ChatMemory {
        return MessageWindowChatMemory.builder()
            .maxMessages(dochibotAiProperties.chat.memory.maxMessages)
            .build()
    }

    /**
     * 대화 API에서 사용하는 ChatClient.
     */
    @Bean
    fun chatClient(builder: ChatClient.Builder, chatMemory: ChatMemory): ChatClient {
        val memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build()

        return builder
            .defaultSystem(
                """
                너는 DochiBot이다.
                - 사용자의 질문에 한국어로 간결하고 정확하게 답한다.
                - 불확실하면 추측하지 말고 필요한 정보를 되물어본다.
                - 개인정보/민감정보는 저장/복원하지 않는다.
                - 추론 과정이나 내부 메모를 노출하지 않는다.
                - `<think>` 같은 태그 없이 최종 답변만 출력한다.
                """.trimIndent()
            )
            .defaultAdvisors(memoryAdvisor)
            .build()
    }
}
