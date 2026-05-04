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
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;
import java.util.Map;

/**
 * 비속어 필터링 Around Advisor
 *
 * LLM 호출을 가로채 PipelineService(L1→L2→L3)를 실행하고,
 * 탐지 시 LLM 서버 요청 없이 즉시 차단 응답을 반환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfanityAroundAdvisor implements CallAroundAdvisor {

    private final PipelineService pipelineService;

    // 가장 먼저 실행되도록 최우선 순위 부여
    private static final int ORDER = 0;

    @Override
    public String getName() {
        return "ProfanityAroundAdvisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest request, CallAroundAdvisorChain chain) {
        String userText = request.userText();
        log.info("[Advisor] 입력 검사 시작 | 입력: '{}'", userText);

        PipelineResponse result = pipelineService.run(userText);
        
        // 컨트롤러에서 접근할 수 있도록 Request 내부에 결과를 살짝 저장해둡니다 (메타데이터 유실 방지)
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            attrs.setAttribute("pipelineResult", result, RequestAttributes.SCOPE_REQUEST);
        }

        if (result.isProfanity()) {
            log.info("[Advisor] 비속어 탐지 -> LLM 호출 즉시 차단 | 탐지 단계: {}", result.detectedBy());
            return buildBlockedResponse(request, result);
        }

        log.info("[Advisor] 안전 판정 -> 실제 LLM 호출 진행");
        return chain.nextAroundCall(request);
    }

    private AdvisedResponse buildBlockedResponse(AdvisedRequest request, PipelineResponse result) {
        String blockedMessage = String.format("[비속어 차단] '%s' | 해당 문장은 %s 단계에서 차단되었습니다.", 
                result.originalText(), result.detectedBy());

        return new AdvisedResponse(
                new ChatResponse(List.of(new Generation(new AssistantMessage(blockedMessage)))), 
                Map.of(
                    "input",             result.originalText(),
                    "isProfanity",       true,
                    "detectedBy",        result.detectedBy(),
                    "profanity_blocked", true,
                    "pipeline_ms",       result.pipelineMs(),
                    "stageResults",      result.stageResults()
                )
        );
    }
}
