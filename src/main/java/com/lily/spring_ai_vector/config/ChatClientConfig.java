package com.lily.spring_ai_vector.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient 빈 설정
 *
 * 왜 별도 Config 로 분리?
 *  - ChatClient.Builder 를 여러 서비스(ProfanityRefineService 등)에서
 *    공유하기 위해 단일 빈으로 관리
 *  - ProfanityRefineService 는 순화 전용 프롬프트를 직접 구성하므로
 *    defaultAdvisors(QuestionAnswerAdvisor) 없이 기본 ChatClient 만 주입
 *  - 향후 retry, logging, rate-limit advisor 를 이곳에서 일괄 적용 가능
 */
@Configuration
public class ChatClientConfig {

    /**
     * 기본 ChatClient 빈
     *
     * QuestionAnswerAdvisor 를 붙이지 않는 이유:
     *  - API 3(ProfanityRefineService)은 RAG 컨텍스트를 직접 프롬프트에 삽입하는 방식 사용
     *  - Advisor 방식은 전체 VectorStore 를 동일 조건으로 검색하므로
     *    API별 threshold/topK 를 다르게 줄 수 없음
     *  - 서비스 레이어에서 직접 similaritySearch 를 제어하면 더 유연함
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
