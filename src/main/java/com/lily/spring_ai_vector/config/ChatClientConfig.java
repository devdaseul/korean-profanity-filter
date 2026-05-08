package com.lily.spring_ai_vector.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring AI ChatClient 빈 설정
 */
@Configuration
public class ChatClientConfig {

    /**
     * 사용자 입력용 ChatClient
     */
    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("당신은 매우 친절하고 도움이 되는 AI 어시스턴트입니다. 사용자의 질문이나 평서문 혼잣말에 대해서도 짧고 간결하게, 딱 1~2문장으로만 상냥하게 한국어로 답변해주세요.")
                .defaultAdvisors(
                        new SimpleLoggerAdvisor()
                )
                .build();
    }

    /**
     * 내부 L3 비속어 판정용 ChatClient (무한루프 방지)
     */
    @Bean
    @Qualifier("llmChatClient")
    public ChatClient llmChatClient(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
