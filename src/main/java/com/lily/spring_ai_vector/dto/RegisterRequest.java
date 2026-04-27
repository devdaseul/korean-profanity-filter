package com.lily.spring_ai_vector.dto;

import java.util.List;

/**
 * [API 1] 관리자 비속어 등록 요청 DTO
 */
public record RegisterRequest(
        List<String> texts,
        List<Long> logIds, // 관리자가 승인할 LLM 로그 ID 리스트
        String category,
        Integer severity,
        String wordType
) {
    // category, severity, wordType은 선택값 - null/빈값이면 기본값으로 보정
    public RegisterRequest {
        if (category == null || category.isBlank()) category = "PROFANITY";
        if (severity == null || severity < 1 || severity > 3) severity = 1;
        if (wordType == null || wordType.isBlank()) wordType = "WORD";
    }
}