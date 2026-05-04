package com.lily.spring_ai_vector.controller;

import com.lily.spring_ai_vector.dto.TextCheckRequest;
import com.lily.spring_ai_vector.dto.PipelineResponse;
import com.lily.spring_ai_vector.service.filter.LocalFilterService;
import com.lily.spring_ai_vector.service.filter.LlmFilterService;
import com.lily.spring_ai_vector.service.filter.RagFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

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

        var chatResponse = chatClient.prompt()
                .user(request.text())
                .call()
                .chatResponse();

        String responseText = chatResponse.getResult().getOutput().getContent();
        
        // Advisor에서 저장해둔 단계별 상세 결과 가져오기
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        PipelineResponse pr = (attrs != null) ? (PipelineResponse) attrs.getAttribute("pipelineResult", RequestAttributes.SCOPE_REQUEST) : null;

        // 차단 텍스트가 반환되었다면 차단 케이스로 판정
        if (responseText != null && responseText.startsWith("[비속어 차단]")) {
            return ResponseEntity.ok(Map.of(
                    "input",       request.text(),
                    "isProfanity", true,
                    "detectedBy",  pr != null ? pr.detectedBy() : "L1-LOCAL",
                    "aiResponse",  responseText,
                    "pipeline_ms", pr != null ? pr.pipelineMs() : 0,
                    "stageResults", pr != null ? pr.stageResults() : "상세 결과 없음"
            ));
        }

        // 안전하게 통과되어 진짜 AI 답변을 받은 케이스
        return ResponseEntity.ok(Map.of(
                "input",       request.text(),
                "isProfanity", false,
                "detectedBy",  "미탐지 (안전)",
                "aiResponse",  responseText,
                "pipeline_ms", pr != null ? pr.pipelineMs() : 0,
                "stageResults", pr != null ? pr.stageResults() : "상세 결과 없음"
        ));
    }
}