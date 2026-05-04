package com.lily.spring_ai_vector.controller;

import com.lily.spring_ai_vector.dto.TextCheckRequest;
import com.lily.spring_ai_vector.dto.PipelineResponse;
import com.lily.spring_ai_vector.service.filter.LocalFilterService;
import com.lily.spring_ai_vector.service.filter.LlmFilterService;
import com.lily.spring_ai_vector.service.filter.RagFilterService;
import com.lily.spring_ai_vector.service.pipeline.PipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 비속어 필터링 시연용 파이프라인 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final LocalFilterService localFilterService;
    private final RagFilterService ragFilterService;
    private final LlmFilterService llmFilterService;
    private final PipelineService pipelineService;
    private final ChatClient chatClient;



    @PostMapping("/l1-local")
    public ResponseEntity<Map<String, Object>> l1LocalFilter(@RequestBody TextCheckRequest request) {
        log.info("[API-L1] POST /api/user/l1-local | 입력: '{}'", request.text());

        Map<String, Object> result = localFilterService.checkAsMap(request.text());
        log.info("[API-L1] 완료 | isProfanity={} detectedBy={}", result.get("isProfanity"), result.get("detectedBy"));
        
        return ResponseEntity.ok(result);
    }


    @PostMapping("/l2-rag")
    public ResponseEntity<Map<String, Object>> l2RagFilter(@RequestBody TextCheckRequest request) {
        log.info("[API-L2] POST /api/user/l2-rag | 입력: '{}'", request.text());

        Map<String, Object> result = ragFilterService.checkAsMap(request.text());
        log.info("[API-L2] 완료 | isProfanity={}", result.get("isProfanity"));

        return ResponseEntity.ok(result);
    }


    @PostMapping("/l3-llm")
    public ResponseEntity<Map<String, Object>> l3LlmFilter(@RequestBody TextCheckRequest request) {
        log.info("[API-L3] POST /api/user/l3-llm | 입력: '{}'", request.text());

        Map<String, Object> result = llmFilterService.checkAsMap(request.text());
        log.info("[API-L3] 완료 | detectedBy={}", result.get("detectedBy"));

        return ResponseEntity.ok(result);
    }


    @PostMapping("/pipeline")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody TextCheckRequest request) {
        log.info("[API-통합] POST /api/user/pipeline | 입력: '{}'", request.text());

        // 1. Advisor 대신 직접 파이프라인 서비스 호출 (L1 -> L2 -> L3 순차 검증)
        PipelineResponse pr = pipelineService.run(request.text());

        // 2. 비속어 차단 시 LLM을 호출하지 않고 즉시 응답 반환 (Fast-Exit)
        if (pr.isProfanity()) {
            String blockedMessage = String.format("[비속어 차단] '%s' | 해당 문장은 %s 단계에서 차단되었습니다.",
                    request.text(), pr.detectedBy());

            return ResponseEntity.ok(Map.of(
                    "input",       request.text(),
                    "isProfanity", true,
                    "detectedBy",  pr.detectedBy(),
                    "aiResponse",  blockedMessage,
                    "pipeline_ms", pr.pipelineMs(),
                    "stageResults", pr.stageResults() != null ? pr.stageResults() : "상세 결과 없음"
            ));
        }

        // 3. 파이프라인(L1, L2, L3)을 모두 무사히 통과한 경우에만 실제 LLM 호출 진행
        String responseText = chatClient.prompt()
                .user(request.text())
                .call()
                .content();

        return ResponseEntity.ok(Map.of(
                "input",       request.text(),
                "isProfanity", false,
                "detectedBy",  "미탐지 (안전)",
                "aiResponse",  responseText,
                "pipeline_ms", pr.pipelineMs(),
                "stageResults", pr.stageResults() != null ? pr.stageResults() : "상세 결과 없음"
        ));
    }
}