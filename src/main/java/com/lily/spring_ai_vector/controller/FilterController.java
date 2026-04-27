package com.lily.spring_ai_vector.controller;

import com.lily.spring_ai_vector.dto.TextCheckRequest;
import com.lily.spring_ai_vector.service.filter.LocalFilterService;
import com.lily.spring_ai_vector.service.filter.LlmFilterService;
import com.lily.spring_ai_vector.service.filter.RagFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 비속어 필터링 시연용 컨트롤러
 *
 *  단계별 API:
 *  L1 → L2 → L3 각 단계를 독립적으로 호출하여 동작 확인 가능
 *
 *  통합 API:
 *  /api/chat — ProfanityAroundAdvisor가 L1→L2→L3를 자동 실행
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FilterController {

    private final LocalFilterService localFilterService;
    private final RagFilterService ragFilterService;
    private final LlmFilterService llmFilterService;
    private final ChatClient chatClient;

    /**
     * L1 전처리 및 로컬 필터 API
     */
    @PostMapping("/filter/l1-local")
    public ResponseEntity<Map<String, Object>> l1LocalFilter(@RequestBody TextCheckRequest request) {
        log.info("▶ [API] POST /filter/l1-local | 입력: '{}'", request.text());
        Map<String, Object> result = localFilterService.checkAsMap(request.text());
        log.info("◀ [API] l1-local 완료 | isProfanity={} detectedBy={}",
                result.get("isProfanity"), result.get("detectedBy"));
        return ResponseEntity.ok(result);
    }

    /**
     * L2 RAG 유사도 검색 API
     */
    @PostMapping("/filter/l2-rag")
    public ResponseEntity<Map<String, Object>> l2RagFilter(@RequestBody TextCheckRequest request) {
        log.info("▶ [API] POST /filter/l2-rag | 입력: '{}'", request.text());
        Map<String, Object> result = ragFilterService.checkAsMap(request.text());
        log.info("◀ [API] l2-rag 완료 | isProfanity={}", result.get("isProfanity"));
        return ResponseEntity.ok(result);
    }

    /**
     * L2(RAG) + L3(LLM) 통합 API
     * - Step1: RAG 유사도 검색으로 1차 판별
     * - Step2: RAG 미탐지 시 LLM 최종 판정 후 llm_judge_log 저장
     * - 비속어 탐지 시 차단 메시지 반환 (텍스트 순화 출력 X)
     *
     * ※ 순화 대신 차단을 선택한 이유:
     *   LLM 순화 결과는 신뢰도가 낮고, 차단해야 llm_judge_log에 원문이 쌓여
     *   지속 학습 루프(log → 관리자 승인 → 벡터 DB 편입)가 정상 동작함
     */
    @PostMapping("/filter/l3-llm")
    public ResponseEntity<Map<String, Object>> l3LlmFilter(@RequestBody TextCheckRequest request) {
        log.info("▶ [API] POST /filter/l3-llm | 입력: '{}'", request.text());
        // Step 1: RAG 유사도 검색
        RagFilterService.Result ragResult = ragFilterService.check(request.text());
        if (ragResult.isProfanity()) {
            log.info("◀ [API] l3-llm 완료 | detectedBy=L2-RAG | matched={}건", ragResult.matchedDocuments().size());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("originalText",    request.text());
            response.put("isProfanity",      true);
            response.put("detectedBy",       "L2-RAG");
            response.put("matchedDocuments", ragResult.matchedDocuments());
            response.put("message",          "RAG 유사도 검색에서 비속어로 탐지되어 차단되었습니다.");
            return ResponseEntity.ok(response);
        }

        // Step 2: RAG 미탐지 → LLM 최종 판정
        log.info("  [API] l3-llm: RAG 미탐지 → LLM 최종 판정 진입");
        LlmFilterService.Result llmResult = llmFilterService.check(request.text());
        log.info("◀ [API] l3-llm 완료 | detectedBy={}", llmResult.isProfanity() ? "L3-LLM" : "미탐지");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("originalText",  llmResult.originalText());
        response.put("isProfanity",    llmResult.isProfanity());
        response.put("detectedBy",     llmResult.isProfanity() ? "L3-LLM" : "미탐지");
        response.put("ragMatched",     ragResult.matchedDocuments());
        response.put("llmResponse",    llmResult.llmRaw());
        response.put("message",        llmResult.isProfanity()
                ? "LLM 최종 판정에서 비속어로 탐지되어 차단되었습니다."
                : "RAG·LLM 모두 통과하여 안전한 문장으로 판정되었습니다.");
        return ResponseEntity.ok(response);
    }

    /**
     * 통합 파이프라인 API
     * - ProfanityAroundAdvisor가 자동으로 L1→L2→L3 실행
     * - 비속어 탐지 시 LLM 호출 없이 즉시 차단, 안전 판정 시 LLM 응답 반환
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody TextCheckRequest request) {
        log.info("▶ [API] POST /chat | 입력: '{}' (ProfanityAroundAdvisor 자동 실행)", request.text());
        String response = chatClient.prompt()
                .user(request.text())
                .call()
                .content();
        log.info("◀ [API] chat 완료 | 응답: '{}'", response);
        return ResponseEntity.ok(Map.of(
                "input",    request.text(),
                "response", response
        ));
    }
}