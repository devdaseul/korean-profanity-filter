package com.lily.spring_ai_vector.advisor;

import com.lily.spring_ai_vector.dto.PipelineResponse;
import com.lily.spring_ai_vector.service.pipeline.PipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * [Spring AI Advisor] 비속어 필터링 Around Advisor
 *
 * ▶ 역할:
 *   ChatClient를 통해 LLM에 메시지가 전달되기 전에
 *   자동으로 비속어 파이프라인(L1→L2→L3)을 실행하여 차단 여부를 판단.
 *
 * ▶ 동작 흐름:
 *   User 입력
 *     → [ProfanityAroundAdvisor 진입]
 *         → PipelineService.run() : L1(로컬) → L2(RAG) → L3(LLM) 순차 실행
 *         → 비속어 탐지 시: LLM 호출 없이 즉시 차단 응답 반환
 *         → 안전 판정 시: chain.nextAroundCall() 로 LLM 호출 진행
 *     → [ProfanityAroundAdvisor 반환]
 *   User 응답
 *
 * ▶ 장점:
 *   - ChatClient를 사용하는 모든 호출에 자동으로 필터링이 적용됨
 *   - FilterController의 /filter/pipeline 엔드포인트를 대체
 *   - 필터링 로직이 AI 대화 흐름 안에 자연스럽게 통합
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfanityAroundAdvisor implements CallAroundAdvisor {

    private final PipelineService pipelineService;

    /** Advisor 우선순위 - 가장 먼저 실행되도록 높은 우선순위 설정 */
    private static final int ORDER = 0;

    @Override
    public String getName() {
        return "ProfanityAroundAdvisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * ChatClient 호출 전후로 비속어 파이프라인을 실행
     *
     * @param request 사용자 입력이 담긴 Advisor 요청
     * @param chain   다음 Advisor 또는 LLM 호출로 이어지는 체인
     * @return LLM 응답 또는 차단 응답
     */
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest request, CallAroundAdvisorChain chain) {
        String userText = request.userText();
        log.info("[ProfanityAdvisor] 입력 검사 시작: '{}'", userText);

        // L1 → L2 → L3 파이프라인 실행
        PipelineResponse result = pipelineService.run(userText);

        if (result.isProfanity()) {
            // 비속어 탐지 → LLM 호출 없이 즉시 차단 응답 반환
            log.info("[ProfanityAdvisor] 비속어 탐지 → LLM 호출 차단 (탐지 단계: {})", result.detectedBy());
            return buildBlockedResponse(request, result);
        }

        // 안전 판정 → 다음 체인(LLM 호출)으로 진행
        log.info("[ProfanityAdvisor] 안전 판정 → LLM 호출 진행");
        return chain.nextAroundCall(request);
    }

    /**
     * 비속어 탐지 시 LLM 응답 대신 반환할 차단 응답 생성
     *
     * ▶ ChatResponse 형태를 맞춰야 ChatClient 흐름이 깨지지 않음
     */
    private AdvisedResponse buildBlockedResponse(AdvisedRequest request, PipelineResponse result) {
        String blockedMessage = String.format(
                "[비속어 차단] '%s' | 탐지 단계: %s",
                result.originalText(), result.detectedBy()
        );

        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(blockedMessage)))
        );

        return new AdvisedResponse(chatResponse, Map.of(
                "profanity_blocked", true,
                "detected_by", result.detectedBy(),
                "pipeline_ms", result.pipelineMs()
        ));
    }
}
