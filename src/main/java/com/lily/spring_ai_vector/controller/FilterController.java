package com.lily.spring_ai_vector.controller;

import com.lily.spring_ai_vector.dto.PipelineResponse;
import com.lily.spring_ai_vector.dto.RagLlmResponse;
import com.lily.spring_ai_vector.dto.TextCheckRequest;
import com.lily.spring_ai_vector.service.filter.RagFilterService;
import com.lily.spring_ai_vector.service.filter.RagLlmFilterService;
import com.lily.spring_ai_vector.service.pipeline.PipelineService;
import com.lily.spring_ai_vector.service.filter.RagFilterService.RagResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 비속어 필터링 시연용 파이프라인 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class FilterController {

    private final RagFilterService ragFilterService;
    private final RagLlmFilterService ragLlmFilterService;
    private final PipelineService pipelineService;


    @PostMapping("/rag")
    public RagResult rag(@RequestBody TextCheckRequest request) {
        log.info("[API-RAG] POST /api/user/rag | 입력: '{}'", request.text());
        return ragFilterService.checkAsResult(request.text());
    }

    @PostMapping("/rag-llm")
    public RagLlmResponse ragLlm(@RequestBody TextCheckRequest request) {
        log.info("[API-RAG+LLM] POST /api/user/rag-llm | 입력: '{}'", request.text());
        return ragLlmFilterService.check(request.text());
    }

    @PostMapping("/pipeline")
    public PipelineResponse pipeline(@RequestBody TextCheckRequest request) {
        log.info("[API-통합] POST /api/user/pipeline | 입력: '{}'", request.text());
        return pipelineService.run(request.text());
    }
}