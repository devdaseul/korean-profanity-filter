package com.lily.spring_ai_vector.dto;

/**
 * [API 3] LLM 순화 결과 응답 DTO
 *
 * 필드 설계 근거:
 *  - originalText: 원문 보존
 *  - refinedText: LLM이 순화한 결과 문장
 *  - ragSearchMs: RAG 검색 소요 시간(ms)
 *  - llmInferenceMs: LLM 추론 소요 시간(ms)
 *  - totalMs: 전체 처리 시간(ms) → SLA 모니터링
 */
public record RefineResponse(
        String originalText,
        String refinedText,
        long ragSearchMs,
        long llmInferenceMs,
        long totalMs
) {}
