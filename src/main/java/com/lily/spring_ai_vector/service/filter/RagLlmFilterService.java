package com.lily.spring_ai_vector.service.filter;

import com.lily.spring_ai_vector.dto.RagLlmResponse;
import com.lily.spring_ai_vector.service.filter.LlmFilterService.LlmResult;
import com.lily.spring_ai_vector.service.filter.RagFilterService.RagResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * RAG + LLM 2단계 통합 시연을 위한 전용 서비스
 * 
 * L1(최적화)를 제외하고 오직 검색(RAG)과 추론(LLM)의 결합만을 보여줍니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagLlmFilterService {

    private final RagFilterService ragFilterService;
    private final LlmFilterService llmFilterService;

    public RagLlmResponse check(String userInput) {
        log.info("[RAG+LLM 통합] 시작 | 입력: '{}'", userInput);
        
        // 1. RAG 필터 우선 수행
        RagResult ragResult = ragFilterService.checkAsResult(userInput);
        if (ragResult.isProfanity()) {
            log.info("[RAG+LLM 통합] RAG 단계에서 즉시 차단됨");
            return new RagLlmResponse(userInput, true, "L2-RAG", ragResult, null);
        }
        
        log.info("[RAG+LLM 통합] RAG 통과 -> LLM 검증 진입");
        // 2. RAG 통과 시 LLM 문맥 판정 수행
        LlmResult llmResult = llmFilterService.checkAsResult(userInput);
        
        return new RagLlmResponse(
                userInput, 
                llmResult.isProfanity(), 
                llmResult.isProfanity() ? "L3-LLM" : "발견되지 않음 (SAFE)", 
                ragResult, 
                llmResult
        );
    }
}
