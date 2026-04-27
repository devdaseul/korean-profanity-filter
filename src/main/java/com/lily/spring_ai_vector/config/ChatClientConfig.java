package com.lily.spring_ai_vector.config;

import com.lily.spring_ai_vector.advisor.ProfanityAroundAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring AI ChatClient 빈 설정
 *
 * ▶ 빈 2개로 분리한 이유 (순환참조 방지):
 *   chatClient → ProfanityAroundAdvisor → PipelineService → LlmFilterService → chatClient
 *   위 순환을 끊기 위해 LlmFilterService 전용 llmChatClient(Advisor 없음)를 별도 정의
 *
 * ▶ chatClient (Primary): 사용자 요청용 - ProfanityAroundAdvisor + SimpleLoggerAdvisor 포함
 * ▶ llmChatClient       : L3 LLM 판정 전용 - Advisor 없이 LLM 직접 호출
 */
@Configuration
public class ChatClientConfig {

    /**
     * 사용자 입력용 ChatClient (ProfanityAroundAdvisor 자동 실행)
     */
    @Primary
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 ProfanityAroundAdvisor profanityAroundAdvisor) {
        return builder
                .defaultAdvisors(profanityAroundAdvisor, new SimpleLoggerAdvisor())
                .build();
    }

    /**
     * L3 LLM 판정 전용 ChatClient (Advisor 없음 - 순환참조 방지)
     */
    @Bean
    @Qualifier("llmChatClient")
    public ChatClient llmChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
