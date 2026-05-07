package com.lily.spring_ai_vector.dto;

import com.lily.spring_ai_vector.service.filter.LlmFilterService.LlmResult;
import com.lily.spring_ai_vector.service.filter.RagFilterService.RagResult;

public record RagLlmResponse(
        String originalText,
        boolean isProfanity,
        String detectedBy,
        RagResult ragResult,
        LlmResult llmResult
) {}
